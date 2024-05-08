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
 *  - simple-omero-client-5.15.0 https://github.com/GReD-Clermont/simple-omero-client
 *  
 *  
 *  = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * date : 2023.11.08
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

		println "Getting image "+imageId
		def imgWrapper = user_client.getImage(imageId) 
		
		def paths = user_client.getMetadata().getOriginalPaths(user_client.getCtx(), imgWrapper.asDataObject())
		if(!paths.isEmpty())
			println "Main original import path for image "+imageId +" : "+paths.get(0)
		else
			println "No path for the current image "+imageId

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
