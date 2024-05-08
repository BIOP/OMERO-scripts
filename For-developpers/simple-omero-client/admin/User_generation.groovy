#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Base username", value="baseuser") baseUsername
#@Long(label="Host group ID") groupId

/* == CODE DESCRIPTION ==
 * This script creates new users, with default username & password
 * 		
 * == INPUTS ==
 *  - credentials 
 *  - Base username : the common part of the username. A suffix number is later added to it.
 * 	
 * == OUTPUTS ==	
 *  - new user on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.18.0 https://github.com/GReD-Clermont/simple-omero-client
 *  
 *  = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * date : 2024.02.22
 * version : v1.0
 * 
 */


// Connection to server
host = "omero-server.epfl.ch"
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "Connected to "+host
	try{		
		//get an exsting group inside which to add the new user(s)
		println "getting group"
		def groups = []
		groups.add(user_client.getGroup(groupId).asDataObject())
		
		// loop on the new users
		for(id=1; id <= 3; id++){ 
			// create the experimenterData
			def experimenter = new ExperimenterData()
			// set the first name
			experimenter.setFirstName("First name")
			// set the last name
			experimenter.setLastName("user"+id)
			// set the username
			def username = baseUsername+id
			// set default password => should be changed
			def password = "jbdfdSSAs*+sd"+id
		
			// create the new experimenter on OMERO
			println "create user "+ (username)
			user_client.getAdminFacility().createExperimenter(user_client.getCtx(), experimenter, username, password, groups, false, false)
		}
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}

/*
 * imports
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.ExperimenterData