/*
 * Copyright (c) Members of the EGEE Collaboration. 2004. 
 * See http://www.eu-egee.org/partners/ for details on the copyright
 * holders.  
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *     http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package org.glite.wms.wmproxy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Properties;

import org.apache.axis2.AxisFault;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.log4j.Logger;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.glite.wms.wmproxy.ws.WMProxyStub;
import org.glite.wms.wmproxy.ws.AuthenticationFaultException;
import org.glite.wms.wmproxy.ws.WMProxyStub.EnableFilePerusal;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetCollectionTemplate;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetDAGTemplate;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetFreeQuota;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetIntParametricJobTemplate;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetJDL;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetJobProxyInfo;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetJobStatus;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetJobTemplate;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetMaxInputSandboxSize;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetOutputFileList;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetPerusalFiles;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetProxyReq;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetSandboxBulkDestURI;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetSandboxDestURI;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetStringParametricJobTemplate;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetTotalQuota;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetTransferProtocols;
import org.glite.wms.wmproxy.ws.WMProxyStub.GetVersion;
import org.glite.wms.wmproxy.ws.WMProxyStub.JobCancel;
import org.glite.wms.wmproxy.ws.WMProxyStub.JobListMatch;
import org.glite.wms.wmproxy.ws.WMProxyStub.JobPurge;
import org.glite.wms.wmproxy.ws.WMProxyStub.JobRegister;
import org.glite.wms.wmproxy.ws.WMProxyStub.JobStart;
import org.glite.wms.wmproxy.ws.WMProxyStub.JobSubmit;
import org.gridsite.www.namespaces.delegation_2.DelegationException_Fault;
import org.gridsite.www.namespaces.delegation_2.WMProxyStub.Destroy;
import org.gridsite.www.namespaces.delegation_2.WMProxyStub.GetInterfaceVersion;
import org.gridsite.www.namespaces.delegation_2.WMProxyStub.GetNewProxyReq;
import org.gridsite.www.namespaces.delegation_2.WMProxyStub.GetServiceMetadata;
import org.gridsite.www.namespaces.delegation_2.WMProxyStub.GetTerminationTime;
import org.gridsite.www.namespaces.delegation_2.WMProxyStub.RenewProxyReq;

import eu.emi.security.authn.x509.impl.CertificateUtils;
import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyGenerator;
import eu.emi.security.authn.x509.proxy.ProxyRequestOptions;
import eu.emi.security.canl.axis2.CANLAXIS2SocketFactory;

/**
 * Allows sending requests to the Workload Manager Proxy (WMProxy) server
 * <p>
 * The class WMProxyAPI represents a client API to access to the WMProxy
 * services provided as Web Services.<BR>
 * It provides a set of methods that allow :
 * <UL>
 * <LI>delegating the credential ;
 * <LI>registering and submitting jobs ;
 * <LI>cancelling the job during its life-cycle ;
 * <LI>retrieving information on the location where the job input sandbox files
 * can be stored ;
 * <LI>retrieving the output sandbox files list ;
 * <LI>retrieving a list of possible matching Computer Elements ;
 * <LI>getting JDL templates ;
 * <LI>getting information on the user disk quota on the server .
 * </UL>
 * </p>
 * <p>
 * Job requirements are expressed by Job Description Language (JDL). The types
 * of jobs supported by the WM service are:
 * <UL>
 * <LI>Normal - a simple application
 * <LI>DAG - a direct acyclic graph of dependent jobs
 * <LI>Collection - a set of independent jobs
 * <LI>Parametric - jobs with JDL's containing some parameters
 * </UL>
 * 
 */

public class WMProxyAPI {

    private static Logger logger = Logger.getLogger(WMProxyAPI.class.getName());

    private URL serviceURL = null;

    private PEMCredential pemCredential = null;
    
    private String certsPath = null;

    private WMProxyStub serviceStub = null;

    private org.gridsite.www.namespaces.delegation_2.WMProxyStub grstStub = null;

    /**
     * Constructor
     * 
     * @param url
     *            WMProxy service URL
     * @param proxyFile
     *            the path location of the user proxy file
     * @throws ServiceException
     *             If any error in calling the service
     * @throws ServiceURLException
     *             malformed service URL specified as input
     * @throws CredentialException
     *             in case of any error with the user proxy file
     */
    public WMProxyAPI(String url, String proxyFile) throws ServiceException, ServiceURLException {
    	
    	File proxy = new File(proxyFile);
    	
    	if(!proxy.exists())
            logger.error("Failed retrieving proxy from path: File not exists.");
        
        certsPath = "/etc/grid-security/certificates";
        this.WMProxyAPIConstructor(url, proxyFile, certsPath);

    }

    /**
     * Constructor that allows setting of the Log4j tool by configuration file
     * 
     * @param url
     *            WMProxy service URL
     * @param proxyFile
     *            the path location of the user proxy file
     * @param logPropFille
     *            the path location of the log4j properties file
     * @throws ServiceException
     *             If any error in calling the service
     * @throws CredentialException
     *             in case of any error with the user proxy file
     * @throws ServiceURLException
     *             malformed service URL specified as input
     */
    public WMProxyAPI(String url, String proxyFile, String certsPath)
        throws ServiceException, ServiceURLException {
    	
    	File proxy = new File(proxyFile);
    	
    	if(!proxy.exists())
            logger.error("Failed retrieving proxy from path: File not exists.");
    	
    	File caFolder = new File(certsPath);
    	
    	if(!caFolder.exists())
            logger.error("Failed retrieving CA certificates from path: Folder not exists.");
    	
        this.WMProxyAPIConstructor(url, proxyFile, certsPath);
    }

    /**
     * Method that serves the constructors of the class
     * 
     * @param url
     *            WMProxy service URL
     * @param proxyFile
     *            the proxy in input as a stream
     * @param logPropFille
     *            the path location of the log4j properties file
     * @throws ServiceException
     *             If any error in calling the service
     * @throws CredentialException
     *             in case of any error with the user proxy file
     * @throws ServiceURLException
     *             malformed service URL specified as input
     */
    private void WMProxyAPIConstructor(String url, String proxyFile, String certsPath)
        throws ServiceException, ServiceURLException {

        logger.debug("INPUT: url=[" + url + "] - proxyFile = [" + proxyFile + "] - certsPath=[" + certsPath
                + "]");
        try {
            this.serviceURL = new URL(url);
        } catch (java.net.MalformedURLException exc) {
            throw new ServiceURLException(exc.getMessage());
        }

        try {
        	char[] emptyPwd = null;
    		
    		this.pemCredential = new PEMCredential(proxyFile, emptyPwd);
    		
		} catch (KeyStoreException e) {
			logger.error("Failed retrieving proxy:" + e.toString());
		} catch (CertificateException e) {
			logger.error("Failed retrieving proxy:" + e.toString());
		} catch (IOException e) {
			logger.error("Failed reading proxy:" + e.toString());
		}
        this.certsPath = certsPath;

        try {
        	
        	setSSLProperties(proxyFile);

            this.serviceStub = new WMProxyStub(serviceURL.toString());

            this.grstStub = new org.gridsite.www.namespaces.delegation_2.WMProxyStub(serviceURL.toString());

        } catch (AxisFault fault) {
            throw new ServiceException(fault.getMessage());
        }

    }
    
    private void setSSLProperties(String proxy){
    	
    	Protocol.registerProtocol(this.serviceURL.getProtocol(), new Protocol(this.serviceURL.getProtocol(), new CANLAXIS2SocketFactory(), this.serviceURL.getPort()));
    	
    	Properties sslConfig = new Properties();
    	sslConfig.put("truststore", this.certsPath);
    	sslConfig.put("crlcheckingmode", "ifvalid");
    	sslConfig.put("proxy", proxy);
    	
    	CANLAXIS2SocketFactory.setCurrentProperties(sslConfig);
    }

    /**
     * Gets a proxy identified by the delegationId string. This method remains
     * to keep compatibility with the version 1.0.0 of WMProxy servers, but it
     * will be soon deprecated.
     * 
     * @param delegationID
     *            the id to identify the delegation
     * @return the string representing the proxy
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws ServiceException
     *             a problem occurred during the remote call to the WMProxy
     *             server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String getProxyReq(String delegationId)
        throws AuthenticationFaultException, ServiceException,
        ServerOverloadedFaultException, AuthorizationFaultException {
        logger.debug("INPUT: delegationId=[" + delegationId + "]");
        try {
        	GetProxyReq getProxyReq = new GetProxyReq();
        	getProxyReq.setDelegationID(delegationId);
            return this.serviceStub.getProxyReq(getProxyReq).getRequest();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.ServerOverloadedFaultException exc) {
            throw new ServerOverloadedFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Gets a proxy identified by the delegationId string. This method can be
     * only used invoking WMProxy servers with versions greater than or equal to
     * 2.0.0; the version of the server can be retrieved by calling the
     * getVersion service.
     * 
     * @param delegationID
     *            the id to identify the delegation
     * @return a string representing the proxy
     * @throws CredentialException
     *             a problem occurred during the operations of retrieving the
     *             proxy
     * @throws ServiceException
     *             a problem occurred during the remote call to the WMProxy
     *             server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @since 1.2.0
     */
    public String grstGetProxyReq(String delegationId)
        throws CredentialException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: delegationId=[" + delegationId + "]");
        try {
        	org.gridsite.www.namespaces.delegation_2.WMProxyStub.GetProxyReq getProxyReq = new org.gridsite.www.namespaces.delegation_2.WMProxyStub.GetProxyReq();
        	getProxyReq.setDelegationID(delegationId);
            return this.grstStub.getProxyReq(getProxyReq).getGetProxyReqReturn();
        } catch (DelegationException_Fault exc) {
            throw new CredentialException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Gets a proxy identified by the delegationId string. This method can be
     * only used invoking WMProxy servers with versions greater than or equal to
     * 3.0.0; the version of the server can be retrieved by calling the
     * getVersion service.
     * 
     * @param delegationID
     *            the id to identify the delegation
     * @return a NewProxyReq representing the proxy
     * @throws CredentialException
     *             a problem occurred during the operations of retrieving the
     *             proxy
     * @throws ServiceException
     *             a problem occurred during the remote call to the WMProxy
     *             server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public org.gridsite.www.namespaces.delegation_2.WMProxyStub.NewProxyReq getNewProxyReq()
        throws CredentialException, ServiceException, ServerOverloadedFaultException {
        try {
        	GetNewProxyReq getNewProxtReq = new GetNewProxyReq();
            return this.grstStub.getNewProxyReq(getNewProxtReq).getGetNewProxyReqReturn();
        } catch (DelegationException_Fault exc) {
            throw new CredentialException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Queries implementation specific meta information about the service This
     * method can be only used invoking WMProxy servers with versions greater
     * than or equal to 3.0.0; the version of the server can be retrieved by
     * calling the getVersion service.
     * 
     * @param the
     *            key of the queried service metadata
     * @return a string representing the information
     * @throws CredentialException
     *             a problem occurred during the operations of retrieving the
     *             proxy
     * @throws ServiceException
     *             a problem occurred during the remote call to the WMProxy
     *             server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String getServiceMetadata(String keyString)
        throws CredentialException, ServiceException, ServerOverloadedFaultException {
        try {
        	GetServiceMetadata key = new GetServiceMetadata();
        	key.setKey(keyString);
            return this.grstStub.getServiceMetadata(key).getGetServiceMetadataReturn();
        } catch (DelegationException_Fault exc) {
            throw new CredentialException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Queries the version of the supported interface. This method can be only
     * used invoking WMProxy servers with versions greater than or equal to
     * 3.0.0; the version of the server can be retrieved by calling the
     * getVersion service.
     * 
     * @return a string representing the version number of the interface
     * @throws CredentialException
     *             a problem occurred during the operations of retrieving the
     *             proxy
     * @throws ServiceException
     *             a problem occurred during the remote call to the WMProxy
     *             server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String getInterfaceVersion()
        throws CredentialException, ServiceException, ServerOverloadedFaultException {
        try {
        	GetInterfaceVersion getInterfaceVersion = new GetInterfaceVersion();
            return this.grstStub.getInterfaceVersion(getInterfaceVersion).getGetInterfaceVersionReturn();
        } catch (DelegationException_Fault exc) {
            throw new CredentialException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the expiration date and time of the credential, associated with
     * the given delegationId. This method can be only used invoking WMProxy
     * servers with versions greater than or equal to 3.0.0; the version of the
     * server can be retrieved by calling the getVersion service.
     * 
     * @param delegationID
     *            the id to identify the delegation
     * @return a calendar with the date and time when the delegated credentials
     *         will expire
     * @throws CredentialException
     *             a problem occurred during the operations of retrieving the
     *             proxy
     * @throws ServiceException
     *             a problem occurred during the remote call to the WMProxy
     *             server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public java.util.Calendar getTerminationTime(String delegationIdString)
        throws CredentialException, ServiceException, ServerOverloadedFaultException {
        try {
        	GetTerminationTime delegationId = new GetTerminationTime();
        	delegationId.setDelegationID(delegationIdString);
            return this.grstStub.getTerminationTime(delegationId).getGetTerminationTimeReturn();
        } catch (DelegationException_Fault exc) {
            throw new CredentialException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Destroys the delegated credentials associated with the given
     * delegationId. This method can be only used invoking WMProxy servers with
     * versions greater than or equal to 3.0.0; the version of the server can be
     * retrieved by calling the getVersion service.
     * 
     * @param delegationID
     *            the id to identify the delegation
     * @throws CredentialException
     *             a problem occurred during the operations of retrieving the
     *             proxy
     * @throws ServiceException
     *             a problem occurred during the remote call to the WMProxy
     *             server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public void destroy(String delegationId)
        throws CredentialException, ServiceException, ServerOverloadedFaultException {
        try {
        	Destroy destroy = new Destroy();
        	destroy.setDelegationID(delegationId);
            this.grstStub.destroy(destroy);
        } catch (DelegationException_Fault exc) {
            throw new CredentialException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Restarts the delegation procedure by asking for a certificate signing
     * request from the server for an already existing delegationId. This method
     * can be only used invoking WMProxy servers with versions greater than or
     * equal to 3.0.0; the version of the server can be retrieved by calling the
     * getVersion service.
     * 
     * @param delegationID
     *            the id to identify the delegation
     * @return a string with the new proxy request, which is to replace the
     *         existing one
     * @throws CredentialException
     *             a problem occurred during the operations of retrieving the
     *             proxy
     * @throws ServiceException
     *             a problem occurred during the remote call to the WMProxy
     *             server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String renewProxyReq(String delegationId)
        throws CredentialException, ServiceException, ServerOverloadedFaultException {
        try {
        	RenewProxyReq renewProxyReq = new RenewProxyReq();
        	renewProxyReq.setDelegationID(delegationId);
            return this.grstStub.renewProxyReq(renewProxyReq).getRenewProxyReqReturn();
        } catch (DelegationException_Fault exc) {
            throw new CredentialException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * This method allows delegating user credential to the WMProxy server: it
     * creates a proxy from the input certificate (got by a previous call to the
     * createProxyfromCertReq server). The created proxy is sent to the server
     * and identified by a delegationId string. This string can be used to call
     * some services accepting a delegationId string as input parameter (like
     * jobRegister, jobSubmit, etc) until its expiration time. This method
     * remains to keep compatibility with the version 1.0.0 of WMProxy servers,
     * but it will be soon deprecated; the version of the server can be
     * retrieved by calling the getVersion service.
     * 
     * @param delegationId
     *            the id used to identify the delegation
     * @param certReq
     *            the certificate request
     * @throws CredentialException
     *             if any error occurs during the creation of the proxy from the
     *             input certificate
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @see #getVersion
     * 
     */
    public void putProxy(String delegationId, String certReq)
        throws AuthenticationFaultException, AuthorizationFaultException, CredentialException, ServiceException,
        ServerOverloadedFaultException {

        String proxy = this.createProxyfromCertReq(certReq);
        try {

            logger.debug("Delegating credential (putProxy)");
            
            WMProxyStub.PutProxy putProxy = new WMProxyStub.PutProxy();
            putProxy.setDelegationID(delegationId);
            putProxy.setProxy(proxy);
            
            this.serviceStub.putProxy(putProxy);

        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * This method allows delegating user credential to the WMProxy server using
     * the GridSite package: it creates a proxy from the input certificate (got
     * by a previous call to the createProxyfromCertReq server). The created
     * proxy is sent to the server and identified by a delegationId string. This
     * string can be used to call some services accepting a delegationId string
     * as input parameter (like jobRegister, jobSubmit, etc) until its
     * expiration time. This method can be only used invoking WMProxy servers
     * with versions greater than or equal to 2.0.0
     * 
     * @param delegationId
     *            the id used to identify the delegation
     * @param certReq
     *            the certificate request
     * @throws CredentialException
     *             a problem occurred during the operations of delegation
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @since 1.2.0
     * @see #getVersion
     */
    public void grstPutProxy(String delegationId, String certReq)
        throws CredentialException, ServiceException, ServerOverloadedFaultException {

        String proxy = this.createProxyfromCertReq(certReq);

        logger.debug("Delegating credential (putProxy)");
        try {
        	WMProxyStub.PutProxy putProxy = new WMProxyStub.PutProxy();
            putProxy.setDelegationID(delegationId);
            putProxy.setProxy(proxy);
            
            this.serviceStub.putProxy(putProxy);
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new CredentialException(exc.getMessage());
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new CredentialException(exc.getMessage());
        } catch (org.glite.wms.wmproxy.ws.ServerOverloadedFaultException exc) {
            throw new ServerOverloadedFaultException(exc.getMessage());
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the Delegated Proxy information identified by the delegationId
     * string
     * 
     * @param delegationId
     *            The id of the delegation created previously (by a getProxyReq
     *            call)
     * @param cfs
     *            Non-default configuration context (proxy file, endpoint URL
     *            and trusted cert location) ; if NULL, the object is created
     *            with the default parameters
     * @return a struct with the information on the input proxy
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws InvalidArgumentFaultException
     *             one or more of the given input parameters is not valid.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @see #getProxyReq
     * @see #grstGetProxyReq
     * @see #putProxy
     * @see #grstPutProxy
     * @since 1.5.3
     */
    public WMProxyStub.ProxyInfoStructType getDelegatedProxyInfo(String delegationId)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: delegationId=[" + delegationId + "]");
        try {
        	WMProxyStub.GetDelegatedProxyInfo getDelegationProxyInfo = new WMProxyStub.GetDelegatedProxyInfo();
        	getDelegationProxyInfo.setDelegationID(delegationId);
            return this.serviceStub.getDelegatedProxyInfo(getDelegationProxyInfo).getItems();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the information related to the proxy used to submit a job that
     * identified by its JobId. This operation needs that a valid proxy
     * (identified by an id string -delegationId string-) has been previously
     * delegated to the endpoint.
     * 
     * @param jobId
     *            the identifier of the job
     * @param cfs
     *            Non-default configuration context (proxy file, endpoint URL
     *            and trusted cert location) ; if NULL, the object is created
     *            with the default parameters
     * @return a struct with the information on the input proxy
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws InvalidArgumentFaultException
     *             one or more of the given input parameters is not valid.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @see #getProxyReq
     * @see #putProxy
     * @see BaseException
     */
    public WMProxyStub.ProxyInfoStructType getJobProxyInfo(String jobId)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobId=[" + jobId + "]");
        try {
        	GetJobProxyInfo getJobProxyInfo = new GetJobProxyInfo();
        	getJobProxyInfo.setJobId(jobId);
            return this.serviceStub.getJobProxyInfo(getJobProxyInfo).getItems();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Gets the version numbers of the WMProxy services
     * 
     * @return the string with the version numbers
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String getVersion()
        throws AuthenticationFaultException, ServiceException, ServerOverloadedFaultException {
        try {
        	GetVersion getVersion = new GetVersion();
            return this.serviceStub.getVersion(getVersion).getVersion();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the information related to the status of the job identified by
     * its JobId. This operation needs that a valid proxy (identified by an id
     * string -delegationId string-) has been previously delegated to the *
     * endpoint.
     * 
     * @param jobId
     *            the identifier of the job
     * @return a struct with the information on the status of the job
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public WMProxyStub.JobStatusStructType getJobStatus(String jobId)
        throws AuthenticationFaultException, AuthorizationFaultException, ServiceException,
        ServerOverloadedFaultException {
        try {
        	GetJobStatus getJobStatus = new GetJobStatus();
        	getJobStatus.setJobId(jobId);
            return this.serviceStub.getJobStatus(getJobStatus).getJobStatusStruct();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.ServerOverloadedFaultException exc) {
            throw new ServerOverloadedFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Registers a job for submission.The unique identifier assigned to the job
     * is returned to the client. This operation only registers the job and
     * assigns it with an identifier. The processing of the job (matchmaking,
     * scheduling etc.) within the WM is not started. The job is "held" by the
     * system in the "Registered" status until the jobStart operation is called.
     * Between the two calls the client can perform all preparatory actions
     * needed by the job to run (e.g. the registration of input data, the upload
     * of the job input sandbox files etc); especially those actions requiring
     * the specification of the job identifier. The service supports
     * registration (and consequently submission) of simple jobs, parametric
     * jobs, partitionable jobs, DAGs and collections of jobs. The description
     * is always provided through a single JDL description. When a clients
     * requests for registration of a complex object, i.e. parametric and
     * partitionable jobs, DAGs and collections of jobs (all those requests
     * represent in fact a set of jobs), the operations returns a structure
     * containing the main identifier of the complex object and the identifiers
     * of all related sub jobs.
     * 
     * @param jdl
     *            the job jdl representation.
     * @param delegationId
     *            the id used to identify the delegation
     * @return the structure containing the id of the registered job
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws InvalidArgumentFaultException
     *             the given JDL expression is not valid
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public WMProxyStub.JobIdStructType jobRegister(String jdl, String delegationId)
        throws AuthenticationFaultException, InvalidArgumentFaultException, ServiceException,
        ServerOverloadedFaultException {
        logger.debug("INPUT: JDL=[" + jdl + "]");
        logger.debug("INPUT: delegationId=[" + delegationId + "]");
        try {
        	JobRegister jobRegister = new JobRegister();
        	jobRegister.setDelegationId(delegationId);
        	jobRegister.setJdl(jdl);
            return this.serviceStub.jobRegister(jobRegister).getJobIdStruct();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Triggers the submission a previously registered job. It starts the actual
     * processing of the registered job within the Workload Manager. It is
     * assumed that when this operation is called, all the work preparatory to
     * the job (e.g. input sandbox upload, registration of input data to the
     * Data Management service etc.) has been completed by the client.<BR>
     * Here follows an example of the sequence of operations to be performed for
     * submitting a job:<BR>
     * <UL>
     * <LI>jobId = jobRegister(JDL)
     * <LI>destURI = getSandboxDestURI(jobID)
     * <LI>transfer of the job Input Sandbox file to the returned destURI (using
     * GridFTP)
     * <LI>jobStart(jobId).Triggers the submission for a previously registered
     * job
     * </UL>
     * 
     * @param jobId
     *            the id of the job
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws JobUnknownFaultException
     *             the given job has not been registered to the system
     * @throws InvalidArgumentFaultException
     *             the given job Id is not valid
     * @throws OperationNotAllowedFaultException
     *             the current job status does not allow requested operation.
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public void jobStart(String jobIdString)
        throws AuthorizationFaultException, AuthenticationFaultException, OperationNotAllowedFaultException,
        InvalidArgumentFaultException, JobUnknownFaultException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobid=[" + jobIdString + "]");
        try {
        	JobStart jobId = new JobStart();
        	jobId.setJobId(jobIdString);
            this.serviceStub.jobStart(jobId);
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.OperationNotAllowedFaultException exc) {
            throw new OperationNotAllowedFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.JobUnknownFaultException exc) {
            throw new JobUnknownFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(exc.getMessage());
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Submits a job: performs registration and triggers the submission The JDL
     * description of the job provided by the client is validated by the
     * service, registered to the LB and finally passed to the Workload Manager.
     * The unique identifier assigned to the job is returned to the client.
     * Usage of this operation (instead of jobRegister + jobStart) is indeed
     * recommended when the job identifier is not needed prior to its submission
     * (e.g. jobs without input sandbox or with a sandbox entirely available on
     * a GridFTP server managed by the client or her organisation). The service
     * supports submission of simple jobs, parametric jobs, partitionable jobs,
     * DAGs and collections of jobs; the description is always provided through
     * a single JDL description. When a clients requests for submission of a
     * complex object, i.e. parametric and partitionable jobs, DAGs and
     * collections of jobs (all those requests represent in fact a set of jobs),
     * the operations returns a structure containing the main identifier of the
     * complex object and the identifiers of all related sub jobs.
     * 
     * @param jobId
     *            the id of the job
     * @param delegationId
     *            the id used to identify the delegation
     * @return the id of the job
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws InvalidArgumentFaultException
     *             the given job Id is not valid
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public WMProxyStub.JobIdStructType jobSubmit(String jdl, String delegationId)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {

        logger.debug("INPUT: JDL=[" + jdl + "]\n");
        logger.debug("INPUT: delegationId=[" + delegationId + "]");
        try {
        	JobSubmit jobSubmit = new JobSubmit();
        	jobSubmit.setDelegationId(delegationId);
        	jobSubmit.setJdl(jdl);
            return this.serviceStub.jobSubmit(jobSubmit).getJobIdStruct();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * This operation cancels a previously submitted job identified by its
     * JobId. If the job is still managed by the WM then it is removed from the
     * WM tasks queue. If the job has been already sent to the CE, the WM simply
     * forwards the request to the CE. For suspending/releasing and sending
     * signals to a submitted job the user has to check that the job has been
     * scheduled to a CE and access directly the corresponding operations made
     * available by the CE service.
     * 
     * @param jobId
     *            the id of the job
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws JobUnknownFaultException
     *             the given job has not been registered to the system
     * @throws InvalidArgumentFaultException
     *             the given job Id is not valid
     * @throws OperationNotAllowedFaultException
     *             the current job status does not allow requested operation.
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public void jobCancel(String jobId)
        throws AuthorizationFaultException, AuthenticationFaultException, OperationNotAllowedFaultException,
        InvalidArgumentFaultException, JobUnknownFaultException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobid=[" + jobId + "]");
        try {
        	JobCancel jobCancel = new JobCancel();
        	jobCancel.setJobId(jobId);
            this.serviceStub.jobCancel(jobCancel);
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            // AuthenticationFault
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            // AuthorizationFault
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.OperationNotAllowedFaultException exc) {
            // OperationNotAllowedFault
            throw new OperationNotAllowedFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            // InvalidArgumentFault
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.JobUnknownFaultException exc) {
            // JobUnknownFault
            throw new JobUnknownFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            // GenericFault ->ServiceException
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (java.rmi.RemoteException exc) {
            // RemoteException->ServiceException
            throw new ServiceException(exc.getMessage());
        } catch (Exception exc) {
            // Exception->ServiceException
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the maximum Input sandbox size (in bytes) a user can count on for
     * a job submission if using the space managed by the WM. This is a static
     * value in the WM configuration (on a job-basis) set by the VO
     * administrator
     * 
     * @return the size of the InputSandbox (in bytes)
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public double getMaxInputSandboxSize()
        throws AuthenticationFaultException, ServiceException, ServerOverloadedFaultException {
        try {
        	GetMaxInputSandboxSize getMaxInputSandboxSize = new GetMaxInputSandboxSize();
            return this.serviceStub.getMaxInputSandboxSize(getMaxInputSandboxSize).getSize();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            // AuthenticationFault
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            // GenericFault ->ServiceException
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (java.rmi.RemoteException exc) {
            // RemoteException->ServiceException
            throw new ServiceException(exc.getMessage());
        } catch (Exception exc) {
            // Exception->ServiceException
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the server available transfer protocols to be used for file
     * transferring operations
     * 
     * @return a list with the protocol strings
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String[] getTransferProtocols()
        throws AuthorizationFaultException, AuthenticationFaultException, ServiceException,
        ServerOverloadedFaultException {
        try {
        	GetTransferProtocols getTransferProtocols = new GetTransferProtocols();
            WMProxyStub.StringList resList = this.serviceStub.getTransferProtocols(getTransferProtocols).getItems();
            return resList.getItem();
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns a list of destination URI's associated to the job, identified by
     * the jobId provided as input, where the job input sandbox files can be
     * uploaded by the client. The location is created in the storage managed by
     * the WM (if needed) and the corresponding URI is returned to the operation
     * caller if no problems has been arised during creation. Files of the job
     * input sandbox that have been referenced in the JDL as relative or
     * absolute paths are expected to be found in the returned location when the
     * job lands on the CE. File upload can be performed by the GridFTP/HTTPS
     * server available on the WMS node(using file-transferring tools like curl
     * and globus-url-copy). The user can also specify in the JDL the complete
     * URI of files that are stored on a GridFTP/HTTPS server (e.g. managed by
     * her organisation); those files will be directly downloaded (by the
     * JobWrapper) on the WN where the job will run without transiting on the
     * WMS machine. The same applies to the output sandbox files list, i.e. the
     * user can specify in the JDL the complete URIs for the files of the output
     * sandbox; those files will be directly uploaded (by the JobWrapper) from
     * the WN to the specified GridFTP/HTTPS servers without transiting on the
     * WMS machine.
     * 
     * @param jobId
     *            string containing the JobId
     * @param protocol
     *            string containing the protocol to use in the returned URI
     *            (Server available protocols are those returned by
     *            getTransferProtocols operation. Possible standard values are:
     *            "all" to get URIs with all available protocols; "default" to
     *            get URIs with the server default protocol not mandatory,
     *            default value is "all")
     * @return the list of the Sandbox destionation URI's strings
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws JobUnknownFaultException
     *             the given job has not been registered to the system
     * @throws InvalidArgumentFaultException
     *             the given job Id is not valid
     * @throws OperationNotAllowedFaultException
     *             the current job status does not allow requested operation.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String[] getSandboxDestURI(String jobId, String protocol)
        throws AuthorizationFaultException, AuthenticationFaultException, OperationNotAllowedFaultException,
        InvalidArgumentFaultException, JobUnknownFaultException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobid=[" + jobId + "] - protocol [" + protocol + "]");
        try {
        	GetSandboxDestURI getSandboxDestURI = new GetSandboxDestURI();
        	getSandboxDestURI.setJobId(jobId);
        	getSandboxDestURI.setProtocol(protocol);
            WMProxyStub.StringList resList = this.serviceStub.getSandboxDestURI(getSandboxDestURI).getPath();
            return resList.getItem();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.OperationNotAllowedFaultException exc) {
            throw new OperationNotAllowedFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.JobUnknownFaultException exc) {
            throw new JobUnknownFaultException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the list of destination URIs associated to a compound job (i.e. a
     * DAG a Collection or a parametric jobs) and all of its sub-jobs in a
     * complex structure containing:
     * <UL>
     * <LI>the job id
     * <LI>the corresponding list of destination URIs (can be more than one if
     * different transfer protocols are supported, e.g. gsiftp, https etc.)
     * <UL>
     * The structure contains an element (structure above) for the compound job
     * id provided (at first position) and one further element for any sub
     * nodes. It contains only one element if the job id provided as imnput is
     * the identifier of a simple job. The location is created in the storage
     * managed by the WMS and the corresponding URI is returned to the operation
     * caller if no problems has been arised during creation. Files of the job
     * input sandbox that have been referenced in the JDL as relative or
     * absolute paths are expected to be found in the returned location when the
     * job lands on the CE. * File upload can be performed by the GridFTP/HTTPS
     * server available on the WMS node(using file-transferring tools like curl
     * and globus-url-copy). The user can also specify in the JDL the complete
     * URI of files that are stored on a GridFTP/HTTPS server (e.g. managed by
     * her organisation); those files will be directly downloaded (by the
     * JobWrapper) on the WN where the job will run without transiting on the
     * WMS machine. The same applies to the output sandbox files list, i.e. the
     * user can specify in the JDL the complete URIs for the files of the output
     * sandbox; those files will be directly uploaded (by the JobWrapper) from
     * the WN to the specified GridFTP/HTTPS servers without transiting on the
     * WMS machine.
     * 
     * @param jobId
     *            string containing the JobId
     * @param protocol
     *            string containing the protocol to use in the returned URI
     *            (Server available protocols are those returned by
     *            getTransferProtocols
     * @return the list of the Sandbox destionation URI's strings
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws JobUnknownFaultException
     *             the given job has not been registered to the system
     * @throws InvalidArgumentFaultException
     *             the given job Id is not valid
     * @throws OperationNotAllowedFaultException
     *             the current job status does not allow requested operation.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public WMProxyStub.DestURIsStructType getSandboxBulkDestURI(String jobId, String protocol)
        throws AuthorizationFaultException, AuthenticationFaultException, OperationNotAllowedFaultException,
        InvalidArgumentFaultException, JobUnknownFaultException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobid=[" + jobId + "] - protocol [" + protocol + "]");
        try {
        	GetSandboxBulkDestURI getSanboxBulkDestURI = new GetSandboxBulkDestURI();
        	getSanboxBulkDestURI.setJobId(jobId);
        	getSanboxBulkDestURI.setProtocol(protocol);
            return this.serviceStub.getSandboxBulkDestURI(getSanboxBulkDestURI).getDestURIsStructType();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.OperationNotAllowedFaultException exc) {
            throw new OperationNotAllowedFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.JobUnknownFaultException exc) {
            throw new JobUnknownFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the total space quota assigned to the user on the storage managed
     * by the WM The fault GetQuotaManagementFault is returned if the quota
     * management is not active on the WM.
     * 
     * @param softLimit
     *            the returned value of the soft limit free quota i.e. the
     *            difference between quota soft limit and user's disk usage.
     * @param hardLimit
     *            the returned value of the hard limit quota (in bytes) i.e. the
     *            real quota limit that cannot be exceeded.
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws GetQuotaManagementFaultException
     *             quota management is not active on the WM.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public void getTotalQuota()
        throws AuthenticationFaultException, GetQuotaManagementFaultException, ServiceException,
        ServerOverloadedFaultException {
        try {
        	GetTotalQuota getTotalQuota = new GetTotalQuota();
            this.serviceStub.getTotalQuota(getTotalQuota);
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GetQuotaManagementFaultException exc) {
            throw new GetQuotaManagementFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the remaining free part of available user disk quota (in bytes).
     * The fault GetQuotaManagementFault is returned if the quota management is
     * not active.
     * 
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws GetQuotaManagementFaultException
     *             quota management is not active on the WM.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public void getFreeQuota()
        throws AuthenticationFaultException, GetQuotaManagementFaultException, ServiceException,
        ServerOverloadedFaultException {
        try {
        	GetFreeQuota getFreeQuota = new GetFreeQuota();
            this.serviceStub.getFreeQuota(getFreeQuota);
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GetQuotaManagementFaultException exc) {
            throw new GetQuotaManagementFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Removes from the WM managed space all files related to the job identified
     * by the jobId provided as input. It can only be applied for job related
     * files that are managed by the WM. E.g. Input/Output sandbox files that
     * have been specified in the JDL through a URI will be not subjected to
     * this management.
     * 
     * @param jobId
     *            the identifier of the job
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws JobUnknownFaultException
     *             the given job has not been registered to the system
     * @throws InvalidArgumentFaultException
     *             the given job Id is not valid
     * @throws OperationNotAllowedFaultException
     *             the current job status does not allow requested operation.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public void jobPurge(String jobId)
        throws AuthorizationFaultException, AuthenticationFaultException, OperationNotAllowedFaultException,
        InvalidArgumentFaultException, JobUnknownFaultException, ServiceException, ServerOverloadedFaultException {

        logger.debug("INPUT: jobid=[" + jobId + "]");
        try {
        	JobPurge jobPurge = new JobPurge();
        	jobPurge.setJobId(jobId);
            this.serviceStub.jobPurge(jobPurge);
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.OperationNotAllowedFaultException exc) {
            throw new OperationNotAllowedFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.JobUnknownFaultException exc) {
            throw new JobUnknownFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the list of URIs where the output files created during job
     * execution have been stored in the WM managed space and the corresponding
     * sizes in bytes. It can only be applied for files of the Output Sandbox
     * that are managed by the WM (i.e. not specified as URI in the JDL).
     * 
     * @param jobId
     *            the identifier of the job
     * @param cfs
     *            Non-default configuration context (proxy file, endpoint URL
     *            and trusted cert location) ; if NULL, the object is created
     *            with the default parameters
     * @return the list of objects containing the file URI and the corresponding
     *         size in bytes
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws JobUnknownFaultException
     *             the given job has not been registered to the system
     * @throws InvalidArgumentFaultException
     *             the given job Id is not valid
     * @throws OperationNotAllowedFaultException
     *             the current job status does not allow requested operation.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public WMProxyStub.StringAndLongList getOutputFileList(String jobId, String protocol)
        throws AuthorizationFaultException, AuthenticationFaultException, OperationNotAllowedFaultException,
        InvalidArgumentFaultException, JobUnknownFaultException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobid=[" + jobId + "] - protocol [" + protocol + "]");
        try {
        	GetOutputFileList getOutputFileList = new GetOutputFileList();
        	getOutputFileList.setJobId(jobId);
        	getOutputFileList.setProtocol(protocol);
            return this.serviceStub.getOutputFileList(getOutputFileList).getOutputFileAndSizeList();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.OperationNotAllowedFaultException exc) {
            throw new OperationNotAllowedFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.JobUnknownFaultException exc) {
            throw new JobUnknownFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the list of CE Ids satisfying the job Requirements specified in
     * the JDL, ordered according to the decreasing Rank. The fault
     * NoSuitableResourcesFault is returned if there are no resources matching
     * job constraints.
     * 
     * @param jdl
     *            the job description
     * @param delegationId
     *            the string used previously to delegate credential
     * @return the list of the found resources and their rank values
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws NoSuitableResourcesFaultException
     *             no suitable resources matching job requirements have been
     *             found.
     * @throws InvalidArgumentFaultException
     *             the given job JDL expression is not valid
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public WMProxyStub.StringAndLongList jobListMatch(String jdl, String delegationId)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        NoSuitableResourcesFaultException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: JDL=[" + jdl + "] - deleagtionId=[" + delegationId + "]");
        try {
        	JobListMatch jobListMatch = new JobListMatch();
        	jobListMatch.setDelegationId(delegationId);
        	jobListMatch.setJdl(jdl);
            return this.serviceStub.jobListMatch(jobListMatch).getCEIdAndRankList();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.NoSuitableResourcesFaultException exc) {
            throw new NoSuitableResourcesFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Enables file perusal functionalities if not disabled with the specific
     * jdl attribute during job register operation. Calling this operation, the
     * user enables perusal for job identified by jobId, for files specified
     * with fileList. After this operation, the URIs of perusal files generated
     * during job execution can be retrieved by calling the getPerusalFiles
     * service An empty fileList disables perusal. This method can be only used
     * invoking WMProxy servers with version greater than or equal to 2.0.0; the
     * version of the server can be retrieved by calling the getVersion service.
     * 
     * @param jobid
     *            the string with the job identifier
     * @param fileList
     *            the list of filenames to be enabled
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred
     * @throws InvalidArgumentFaultException
     *             the given jobId is not valid
     * @throws OperationNotAllowedFaultException
     *             perusal was disabled with the specific jdl attribute.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @see #getPerusalFiles
     * @see #getVersion
     */
    public void enableFilePerusal(String jobId, String[] fileList)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        JobUnknownFaultException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobId=[" + jobId + "]");
        try {
            WMProxyStub.StringList fList = new WMProxyStub.StringList();
            for (String item : fileList) {
                fList.addItem(item);
            }
            EnableFilePerusal enableFilePerusal = new EnableFilePerusal();
            enableFilePerusal.setFileList(fList);
            enableFilePerusal.setJobId(jobId);
            this.serviceStub.enableFilePerusal(enableFilePerusal);
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.JobUnknownFaultException exc) {
            throw new JobUnknownFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Gets the URIs of perusal files generated during job execution for the
     * specified file file. If allChunks is set to true all perusal URIs will be
     * returned; also the URIs already requested with a previous getPerusalFiles
     * operation. Default value is false. Perusal files have to be presiuosly
     * enabled by calling the enableFilePerusal service This method can be only
     * used invoking WMProxy servers with version greater than or equal to
     * 2.0.0; the version of the server can be retrieved by calling the
     * getVersion service.
     * 
     * @param jobid
     *            the string with the job identifier
     * @param allchuncks
     *            boolean value to specify when to get all chuncks
     * @param jobid
     *            the string with the job identifier
     * @param file
     *            the name of the perusal file be enabled
     * @param jobid
     *            the string with the job identifier
     * @param file
     *            the name of the perusal file be enabled
     * @param protocol
     *            string containing the protocol to use in the returned URI
     *            (Server available protocols are those returned by
     *            getTransferProtocols
     * @throws AuthenticationFaultException
     *             An authentication problem occurred
     * @throws AuthorizationFaultException
     *             The user is not authorized to perform this operation
     * @throws InvalidArgumentFaultException
     *             If the given jobId is not valid
     * @throws JobUnknownFaultException
     *             The provided jobId has not been registered to the system
     * @throws OperationNotAllowedFaultexception
     *             perusal was disabled with the specific jdl attribute
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @see #enableFilePerusal
     * @see #getVersion
     */
    public String[] getPerusalFiles(String jobId, String file, boolean allchunks, String protocol)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        JobUnknownFaultException, ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobId=[" + jobId + "] - file=[" + file + "] - allchunck=[" + allchunks + "] - protocol ["
                + protocol + "]");
        try {
        	GetPerusalFiles perusalFiles = new GetPerusalFiles();
        	perusalFiles.setAllChunks(allchunks);
        	perusalFiles.setFile(file);
        	perusalFiles.setJobId(jobId);
        	perusalFiles.setProtocol(protocol);
            WMProxyStub.StringList response = this.serviceStub.getPerusalFiles(perusalFiles).getFileList();

            return response.getItem();

        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.JobUnknownFaultException exc) {
            throw new JobUnknownFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns a JDL template for the requested job type.
     * 
     * @param jobType
     *            the job type description
     * @param executable
     *            the exacutable filename
     * @param arguments
     *            the arguments of the executable
     * @param requirements
     *            a string representing the expression describing all the Job
     *            requirements (which is an attribute of boolean type)
     * @param rank
     *            a string representing the expression for the rank (which is an
     *            attribute of double type) of the resources
     * @return the jdl template of the job
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws InvalidArgumentFaultException
     *             one or more of the given input parameters is not valid
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String getJobTemplate(WMProxyStub.JobType[] jobType, String executable, String arguments,
            String requirements, String rank)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {

        logger.debug("INPUT: executable=[" + executable + "] - arguments=[" + arguments + "]");
        logger.debug("INPUT: requirements=[" + requirements + "] - rank=[" + rank + "]");
        try {
            WMProxyStub.JobTypeList jList = new WMProxyStub.JobTypeList();
            for (WMProxyStub.JobType item : jobType) {
                jList.addJobType(item);
            }
            GetJobTemplate getJobTemplate = new GetJobTemplate();
            getJobTemplate.setArguments(arguments);
            getJobTemplate.setExecutable(executable);
            getJobTemplate.setJobType(jList);
            getJobTemplate.setRank(rank);
            getJobTemplate.setRequirements(requirements);
            return this.serviceStub.getJobTemplate(getJobTemplate).getJdl();

        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns a JDL template for a DAG.
     * 
     * @param dependencies
     *            the description of the DAG
     * @param requirements
     *            a string representing the expression describing all the Job
     *            requirements (which is an attribute of boolean type)
     * @param rank
     *            a string representing the expression for the rank (which is an
     *            attribute of double type) for all the nodes of the dag
     * @return the jdl template of the DAG
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws InvalidArgumentFaultException
     *             one or more of the given input parameters is not valid
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * 
     */
    public String getDAGTemplate(WMProxyStub.GraphStructType dependencies, String requirements, String rank)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: requirements=[" + requirements + "] - rank=[" + rank + "]");
        try {
        	GetDAGTemplate getDAGTemplate = new GetDAGTemplate();
        	getDAGTemplate.setDependencies(dependencies);
        	getDAGTemplate.setRank(rank);
        	getDAGTemplate.setRequirements(requirements);
            return this.serviceStub.getDAGTemplate(getDAGTemplate).getJdl();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns a JDL template for a collection of jobs, that is a set of
     * independent jobs that can be submitted, controlled and monitored as a
     * single entity.
     * 
     * @param jobNumber
     *            integer representing the number of jobs of the collection.
     * @param requirements
     *            a string representing the expression describing all the Job
     *            requirements (which is an attribute of boolean type)
     * @param rank
     *            a string representing the expression for the rank (which is an
     *            attribute of double type) of the resources
     * @return the jdl template of the collection
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws InvalidArgumentFaultException
     *             one or more of the given input parameters is not valid.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String getCollectionTemplate(int jobNumber, String requirements, String rank)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: requirements=[" + requirements + "] - rank=[" + rank + "]");
        try {
        	GetCollectionTemplate getCollectionTemplate = new GetCollectionTemplate();
        	getCollectionTemplate.setJobNumber(jobNumber);
        	getCollectionTemplate.setRequirements(requirements);
        	getCollectionTemplate.setRank(rank);
            return this.serviceStub.getCollectionTemplate(getCollectionTemplate).getJdl();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            // AuthenticationFault
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            // AuthorizationFault
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            // InvalidArgumentFault
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            // GenericFault ->ServiceException
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (java.rmi.RemoteException exc) {
            // RemoteException->ServiceException
            throw new ServiceException(exc.getMessage());
        } catch (Exception exc) {
            // Exception->ServiceException
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns a JDL template for a parametric of job, which is a job having one
     * or more parametric attributes in the JDL. The parametric attributes vary
     * their values according to the "Parameter" attribute specified in the JDL
     * itself (in this case the parametere has to be an integer). The submission
     * of a Parametric job results in the submission of a set of jobs having the
     * same descritpion apart from the parametrised attribute. They can be
     * however controlled and monitored as a single entity.
     * 
     * @param attributes
     *            list of strings representing the parametric JDL attributes
     * @param param
     *            integer representing the value of the Parameter attribute
     * @param paramStart
     *            integer representing the start value for varying the
     *            parametric attributes
     * @param paramStep
     *            intege representing the step of each variation
     * @param requirements
     *            a string representing the expression describing all the Job
     *            requirements (which is an attribute of boolean type)
     * @param rank
     *            a string representing the expression for the rank (which is an
     *            attribute of double type) of the resources
     * @return the jdl template
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws InvalidArgumentFaultException
     *             one or more of the given input parameters is not valid.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String getIntParametricJobTemplate(String[] attributes, int param, int parameterStart, int parameterStep,
            String requirements, String rank)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: param=[" + param + "] - parameterStart=[" + parameterStart + "] - parameterStep=["
                + parameterStep + "]");
        logger.debug("INPUT: requirements=[" + requirements + "] - rank=[" + rank + "]");
        try {
            WMProxyStub.StringList attrList = new WMProxyStub.StringList();
            for (String item : attributes) {
                attrList.addItem(item);
            }
            GetIntParametricJobTemplate getIntParametricJobTemplate = new GetIntParametricJobTemplate();
            getIntParametricJobTemplate.setAttributes(attrList);
            getIntParametricJobTemplate.setParam(param);
            getIntParametricJobTemplate.setParameterStart(parameterStart);
            getIntParametricJobTemplate.setParameterStep(parameterStep);
            getIntParametricJobTemplate.setRequirements(requirements);
            getIntParametricJobTemplate.setRank(rank);
            return this.serviceStub.getIntParametricJobTemplate(getIntParametricJobTemplate).getJdl();

        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns a JDL template for a parametric of job, which is a job having one
     * or more parametric attributes in the JDL. The parametric attributes vary
     * their values according to the "Parameter" attribute specified in the JDL
     * itself (in this case the parametere has to be a list of strings). The
     * submission of a Parametric job results in the submission of a set of jobs
     * having the same descritpion apart from the value of the parametric
     * attributes. They can be however controlled and monitored as a single
     * entity.
     * 
     * @param attributes
     *            list of strings representing the parametric JDL attributes
     * @param param
     *            list of strings representing the values of the Parameter
     *            attribute
     * @param requirements
     *            a string representing the expression describing all the Job
     *            requirements (which is an attribute of boolean type)
     * @param rank
     *            a string representing the expression for the rank (which is an
     *            attribute of double type) of the resources
     * @return the jdl template
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws InvalidArgumentFaultException
     *             one or more of the given input parameters is not valid
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     */
    public String getStringParametricJobTemplate(String[] attributes, String[] param, String requirements, String rank)

        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {
        try {
            WMProxyStub.StringList attrList = new WMProxyStub.StringList();
            for (String item : attributes) {
                attrList.addItem(item);
            }

            WMProxyStub.StringList pList = new WMProxyStub.StringList();
            for (String item : param) {
                pList.addItem(item);
            }
            GetStringParametricJobTemplate getStringParametricJobTemplate = new GetStringParametricJobTemplate();
            getStringParametricJobTemplate.setAttributes(attrList);
            getStringParametricJobTemplate.setParam(pList);
            getStringParametricJobTemplate.setRequirements(requirements);
            getStringParametricJobTemplate.setRank(rank);
            return this.serviceStub.getStringParametricJobTemplate(getStringParametricJobTemplate).getJdl();

        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }

    /**
     * Returns the JDL string which identifier is the input JobId
     * 
     * @param jobid
     *            the identifier of the job
     * @param type
     *            the type of the JDL to be retrieved (either ORIGINAL or
     *            REGISTERED)
     * @return the string with the JDL
     * @throws AuthenticationFaultException
     *             a generic authentication problem occurred.
     * @throws AuthorizationFaultException
     *             if the client is not authorized to perform this operation.
     * @throws InvalidArgumentFaultException
     *             one or more of the given input parameters is not valid.
     * @throws ServiceException
     *             If any other error occurs during the execution of the remote
     *             method call to the WMProxy server
     * @throws ServerOverloadedFaultException
     *             the server is too much busy to attend the requested operation
     * @since 1.5.3
     */
    public String getJDL(String jobId, WMProxyStub.JdlType type)
        throws AuthorizationFaultException, AuthenticationFaultException, InvalidArgumentFaultException,
        ServiceException, ServerOverloadedFaultException {
        logger.debug("INPUT: jobId=[" + jobId + "] - JdlType=[" + type + "]");
        try {
        	GetJDL getJDL = new GetJDL();
        	getJDL.setJobId(jobId);
        	getJDL.setType(type);
            return this.serviceStub.getJDL(getJDL).getJdl();
        } catch (org.glite.wms.wmproxy.ws.AuthenticationFaultException exc) {
            throw new AuthenticationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.AuthorizationFaultException exc) {
            throw new AuthorizationFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.InvalidArgumentFaultException exc) {
            throw new InvalidArgumentFaultException(this.createExceptionMessage(exc));
        } catch (org.glite.wms.wmproxy.ws.GenericFaultException exc) {
            throw new ServiceException(this.createExceptionMessage(exc));
        } catch (Exception exc) {
            throw new ServiceException(exc.getMessage());
        }
    }
    
    /**
     * Return the URL of the service.
     * @return the URL of the service.
     */
    public String getEndPoint() {
		return serviceURL.toString();
	}

    /**
     * Generates a proxy from the input certificate and from the user proxy file
     * on the user local machine (which path has to be specified as input
     * parameter in one of the class costructors) This service is called after
     * the getProxyReq the input proxy string of this service is the string got
     * by getProxyReq.
     * 
     * @param certReq
     *            Service certificate request
     * @return the generated proxy certificate
     * @throws CredentialException
     *             in case of any error with the local user proxy
     */

    private String createProxyfromCertReq(String certReq) throws CredentialException {
    	try{
    		
            PrivateKey pKey = this.pemCredential.getKey();
            
            PEMParser pemParser = new PEMParser(new StringReader(certReq));
            PKCS10CertificationRequest proxyReq = (PKCS10CertificationRequest) pemParser.readObject();
            pemParser.close();
            
            ProxyRequestOptions csrOpt = new ProxyRequestOptions(this.pemCredential.getCertificateChain(), proxyReq);
            
            X509Certificate[] certChain = ProxyGenerator.generate(csrOpt, pKey);
            
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            for (X509Certificate tmpcert : certChain) {
                CertificateUtils.saveCertificate(outStream, tmpcert, CertificateUtils.Encoding.PEM);
            }
            
            return outStream.toString();
    		
    	} catch (Exception e) {
            throw new CredentialException(e.getMessage());
        }

    }

    private String createExceptionMessage(Exception exc) {

        StringBuffer message = new StringBuffer();

        WMProxyStub.BaseFaultType fault = null;

        if (exc instanceof org.glite.wms.wmproxy.ws.AuthenticationFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.AuthenticationFaultException) exc).getFaultMessage()
                    .getAuthenticationFault();
        } else if (exc instanceof org.glite.wms.wmproxy.ws.JobUnknownFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.JobUnknownFaultException) exc).getFaultMessage().getJobUnknownFault();
        } else if (exc instanceof org.glite.wms.wmproxy.ws.NoSuitableResourcesFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.NoSuitableResourcesFaultException) exc).getFaultMessage()
                    .getNoSuitableResourcesFault();
        } else if (exc instanceof org.glite.wms.wmproxy.ws.AuthorizationFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.AuthorizationFaultException) exc).getFaultMessage()
                    .getAuthorizationFault();
        } else if (exc instanceof org.glite.wms.wmproxy.ws.GetQuotaManagementFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.GetQuotaManagementFaultException) exc).getFaultMessage()
                    .getGetQuotaManagementFault();
        } else if (exc instanceof org.glite.wms.wmproxy.ws.OperationNotAllowedFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.OperationNotAllowedFaultException) exc).getFaultMessage()
                    .getOperationNotAllowedFault();
        } else if (exc instanceof org.glite.wms.wmproxy.ws.GenericFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.GenericFaultException) exc).getFaultMessage().getGenericFault();
        } else if (exc instanceof org.glite.wms.wmproxy.ws.InvalidArgumentFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.InvalidArgumentFaultException) exc).getFaultMessage()
                    .getInvalidArgumentFault();
        } else if (exc instanceof org.glite.wms.wmproxy.ws.ServerOverloadedFaultException) {
            fault = ((org.glite.wms.wmproxy.ws.ServerOverloadedFaultException) exc).getFaultMessage()
                    .getServerOverloadedFault();
        }

        if (fault != null) {
            message.append("Description: ").append(fault.getDescription()).append("\n");
            message.append("Error code : ").append(fault.getErrorCode()).append("\n");
            message.append("Cause:\n");
            for (String cause : fault.getFaultCause()) {
                message.append("  ").append(cause).append("\n");
            }
            message.append("Method: ").append(fault.getMethodName()).append("\n");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            message.append("TimeStamp: ").append(sdf.format(fault.getTimestamp().getTime())).append("\n");
        }

        if (exc instanceof DelegationException_Fault) {
            DelegationException_Fault dfException = (DelegationException_Fault) exc;
            org.gridsite.www.namespaces.delegation_2.WMProxyStub.DelegationException dFault = dfException
                    .getFaultMessage();

            message.append("Description: ").append(dFault.getMsg()).append("\n");

        }

        return message.toString();
    }
}
