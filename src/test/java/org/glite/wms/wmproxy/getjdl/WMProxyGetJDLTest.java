/*
Copyright (c) Members of the EGEE Collaboration. 2004.
See http://www.eu-egee.org/partners/ for details on the
copyright holders.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


/*
 * Copyright (c) Members of the EGEE Collaboration. 2004.
 * See http://eu-egee.org/partners/ for details on the copyright holders.
 * For license conditions see the license file or http://eu-egee.org/license.html
 */

package org.glite.wms.wmproxy.getjdl ;

// WMP-API-JAVA
import org.glite.wms.wmproxy.WMProxyAPI ;
import org.glite.wms.wmproxy.ws.WMProxyStub.JdlType ;
// INPUT OBJECTS
import java.io.InputStreamReader ;
import java.io.BufferedReader ;

/*
*
*	Test of  "getJDL" method in org.glite.wms.wmproxy.WMProxyAPI
*
*/

public class WMProxyGetJDLTest {

	public WMProxyGetJDLTest  ( ) { }

	/*
	*	starts the test
	*	@param url the service URL
	*	@param jobId the job identifier
	*	@param type the type of JDL to be retrieved
	*	@param proxyFile the path location of the user proxy file
	*	@param certsPath the path location of the directory containing all the Certificate Authorities files
	*	@throws.Exception if any error occurs
	*/
	public static void runTest ( String url, String jobId, JdlType type, String proxyFile, String certsPath  ) throws  java.lang.Exception {
		String jdl = "";
		WMProxyAPI client = null;
		// Prints out the input parameters
		System.out.println ("TEST : getJDL");
		System.out.println ("************************************************************************************************************************************");
		System.out.println ("WS URL	 		= [" + url + "]" );
		System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		System.out.println ("JOBID	 		= [" +jobId + "]" );
		System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		System.out.println ("JDL type	 	= [" + type + "]" );
		System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		System.out.println ("proxy	 		= [" + proxyFile + "]" );
		System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		if (certsPath.length()>0){
			System.out.println ("CAs path		= [" + certsPath+ "]" );
			System.out.println ("--------------------------------------------------------------------------------------------------------------------------------");
		  	client = new WMProxyAPI ( url, proxyFile, certsPath ) ;
		} else {
		 	 client = new WMProxyAPI ( url, proxyFile ) ;
		 }
		System.out.println ("Testing....");
		jdl = client.getJDL(jobId, type) ;

		// result
		System.out.println ("RESULT - JDL:");
		System.out.println ("===================================================");
		System.out.println (jdl);
		System.out.println ("===================================================");
		System.out.println ("end of the test" );
	 }

	 private static JdlType askType( ) {
		InputStreamReader isr = null ;
		BufferedReader stdin = null ;
		String input = "";
		Integer conv = null;
		int value = 0;
		JdlType type = JdlType.ORIGINAL;
		System.out.println( "Choose JdlType to be retrieved:" );
		System.out.println ("=============================");
		System.out.println ("1 - ORIGINAL");
		System.out.println ("2 - REGISTERED");
		isr = new InputStreamReader( System.in );
		stdin = new BufferedReader( isr );
		while (true) {
			System.out.println ("---------------------------");
			System.out.println ("Choose one type in the list [1|2]");
			try {
				input = stdin.readLine();
			} catch (java.io.IOException exc){
				continue;
			}
			try {
				conv = new Integer(input.trim());
				value = conv.intValue();
				if (value == 1 || value == 2){
					break;
				}
			} catch ( NumberFormatException exc){
				// invalid value: repeats this loop
			}
		}
		if (value==1) {
			type = JdlType.ORIGINAL;
		} else if (value==2) {
			type = JdlType.REGISTERED;
		}
		return type;
    	}
	public static void main(String[] args) throws java.lang.Exception {
		// test input parameters
		String url = "" ;
		String jobId = "" ;
		String proxyFile = "";
		String certsPath = "";
		JdlType type ;
		try {
			// input parameters
			if ((args == null) || (args.length < 3)){
				throw new Exception ("error: some mandatory input parameters are missing (<WebServices URL> <jobId> <proxyFile> [CAs paths (optional)] )");
			} else if (args.length > 4) {
			 	 throw new Exception ("error: too many parameters\nUsage: java <package>.<class> <WebServices URL> <jobId> <proxyFile> [CAs paths (optional)] )");
			}
			url = args[0];
			jobId = args[1];
			proxyFile = args[2];
			if (args.length == 4) {
				certsPath = args[3];
			} else  {
				certsPath = "";
			}
			// Type of the JDL to be retrieved (either original or registered)
			type = askType ( );
			// Launches the test
			runTest ( url, jobId, type, proxyFile, certsPath);
		} catch (Exception exc){
			System.out.println (exc.toString( ));
		}
	 }

 }
