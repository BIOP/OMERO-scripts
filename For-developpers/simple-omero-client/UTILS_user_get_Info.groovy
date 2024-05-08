#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD

/* 
 * == INPUTS ==
 *  - credentials 
 * 
 * == OUTPUTS ==
 *  - print user information on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.1 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 01.09.2022
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2022
 * 
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * == HISTORY ==
 * - 2023.06.19 : Remove unnecessary imports 
 */

/**
 * Main. Connect to OMERO, display user information and disconnect from OMERO
 * 
 */
 
// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
		getUserInformation(user_client)
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}

}else{
	println "Not able to connect to "+host
}


/**
 * Display information about the logged-in user
 * 
 * input
 * 		user_client : OMERO client
 */
def getUserInformation(user_client){
	println "Session ID : "+ user_client.getSessionId()
	println "User ID : " + user_client.getId()
	ExperimenterWrapper ew = user_client.getUser()
	println "User name : " + ew.getFirstName() + " " + ew.getLastName()
	println "email : "  + ew.getEmail()
	println "Institution : " + ew.getInstitution()
	GroupWrapper[] gw = ew.getGroups().toArray()
	println "User groups : " 
	for (GroupWrapper gpw : gw)
		println "-  " + gpw.getName() + "(id : "+ gpw.getId() +") ; Description : " +gpw.getDescription()
}


/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.meta.*