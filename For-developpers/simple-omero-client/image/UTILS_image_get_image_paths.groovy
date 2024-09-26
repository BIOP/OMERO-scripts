#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Image ID", value=119273, required=false) imageId

/* == CODE DESCRIPTION ==
 * This script gets the original import path of an image
 * 		
 * == INPUTS ==
 *  - credentials 
 *  - image Id
 * 	
 * 
 * == OUTPUTS ==	
 *  - orginial import path
 * 
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.19.0 https://github.com/GReD-Clermont/simple-omero-client
 *  
 *  
 *  = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * date : 2023.11.08
 * version : v2.0
 *
 * = HISTORY = 
 * 2024.09.26 : update method to get original path directly from simple-omero-client --v2.0
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
		def imgWrapper = user_client.getImage(imageId)
		
		println "Getting oringinal import paths for image "+imageId
		imgWrapper.getOriginalPaths(user_client).each{println it}
		
		println "Getting server paths for image "+imageId
		imgWrapper.getManagedRepositoriesPaths(user_client).each{println it}

	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
		
}else{
	println "Not able to connect to "+host
}

return

/*
 * imports
 */
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
