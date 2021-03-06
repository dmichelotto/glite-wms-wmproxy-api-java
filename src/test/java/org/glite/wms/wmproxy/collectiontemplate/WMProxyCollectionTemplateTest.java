/*
*	Copyright (c) Members of the EGEE Collaboration. 2004.
*	See http://www.eu-egee.org/partners/ for details on the
*	copyright holders.
*
*	Licensed under the Apache License, Version 2.0 (the "License");
*	you may not use this file except in compliance with the License.
*	You may obtain a copy of the License at
*	 
*	     http://www.apache.org/licenses/LICENSE-2.0
*	 
*	Unless required by applicable law or agreed to in writing, software
*	distributed under the License is distributed on an "AS IS" BASIS,
*	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
*	either express or implied.
*	See the License for the specific language governing permissions and
*	limitations under the License.
*/

package org.glite.wms.wmproxy.collectiontemplate ;

import org.glite.wms.wmproxy.WMProxyAPI ;
import org.glite.wms.wmproxy.common.GetParameters ;

/*
	Test of "getCollectionTemplate" method in org.glite.wms.wmproxy.WMProxyAPI

	example of configuration file :
	jobnumber=3
	requirements=true
	rank=other.GlueCEStateEstimatedResponseTime


*/

public class WMProxyCollectionTemplateTest {

	public WMProxyCollectionTemplateTest  ( ) { }

	/*
	*	Starts the test
	*	@param url service URL
	*  	@param configFile path location of the configuration file
	*	@param proxyFile the path location of the user proxy file
	*	@param certsPath the path location of the directory containing all the Certificate Authorities files
	*	@throws.Exception if any error occurs
	*/
	public static void runTest ( String url, String configFile, String proxyFile, String certsPath  ) throws java.lang.Exception {

		// input parameters for the service
		int jobNumber = 0;
		String rank = "";
		String requirements = "";
		WMProxyAPI client = null;

		// output result
		String result = "";

		// Prints out the input parameters
		System.out.println ("TEST : CollectionTemplate");
		System.out.println ("************************************************************************************************************************************");
		System.out.println ("WS URL	 		= [" + url + "]" );
		System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		System.out.println ("proxy	 		= [" + proxyFile + "]" );
		System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		System.out.println ("config-File	 	= [" + configFile + "]" );
		System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		if (certsPath.length()>0){
			System.out.println ("CAs path		= [" + certsPath+ "]" );
			System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		  	client = new WMProxyAPI ( url, proxyFile, certsPath ) ;
		} else {
		 	 client = new WMProxyAPI ( url, proxyFile ) ;
		 }
		// Read configuration file ----------------------------
		System.out.println ("==================================================================================================");
		GetParameters parameters = new GetParameters(  configFile ) ;
		// job	number
		jobNumber = parameters.getIntValue( GetParameters.JOBNUMBER );
		System.out.println ( "jobnumber	= [" + jobNumber + "]");
		// rank
		rank =parameters.getStringValue ( GetParameters.RANK );
		System.out.println ( "rank		= [" + rank + "]");
		// requirements
		requirements = parameters.getStringValue ( GetParameters.REQUIREMENTS );
		System.out.println ( "requirements	= [" + requirements + "]");

		System.out.println ("==================================================================================================");
		// end reading configuration file ----------------------------
		// testing ...
		System.out.println ("Testing....");
		result = client.getCollectionTemplate( jobNumber, requirements, rank);
		// result
		System.out.println ("RESULT");
		System.out.println ("=========================================================================================================================================================");
		System.out.println (result);
		System.out.println ("=========================================================================================================================================================");
		System.out.println ("End of the test" );
	}
	/*
		main
	*/
	public static void main(String[] args) throws java.lang.Exception {
		// test input parameters
		String url = "" ;
		String configFile = "" ;
		String proxyFile = "";
		String certsPath = "";
		try {
			// input parameters
			if ((args == null) || (args.length < 3)){
				throw new Exception ("error: some mandatory input parameters are missing (<WebServices URL> <proxyFile> <config-File> [CAs paths (optional)])");
			} else if (args.length > 4) {
			 	 throw new Exception ("error: too many parameters\nUsage: java <package>.<class> <WebServices URL> <proxyFile> <config-File> [CAs paths (optional)] )");
			}
			url = args[0];
			configFile = args[1];
			proxyFile = args[2];
			if (args.length == 4) {
				certsPath = args[3];
			} else  {
			certsPath = "";
			}
			runTest ( url, configFile, proxyFile, certsPath);
		} catch (Exception exc){
			System.out.println (exc.toString( ));
		}
 	 }
 }
