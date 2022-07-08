#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD



/**
 * Main. Connect to OMERO, display user information and disconnect from OMERO
 * 
 */
 
// create the client and connect to the host
Client user_client = new Client()
host = "omero-server.epfl.ch"
port = 4064

user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\n Connected to "+host +"\n"
	
	try{
		getUserInformation(user_client)
		
	} finally{
		user_client.disconnect()
		println "\n Disonnected "+host
	}
	
	return
	
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
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import fr.igred.omero.meta.*