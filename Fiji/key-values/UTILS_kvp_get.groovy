#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type


/**
 * Main. Connect to OMERO, process tags and disconnect from OMERO
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
		
		switch (object_type){
		case "image":	
			processKVP( user_client, user_client.getImage(id) )
			break	
		case "dataset":
			processKVP( user_client, user_client.getDataset(id) )
			break
		case "project":
			processKVP( user_client, user_client.getProject(id) )
			break
		case "well":
			processKVP( user_client, user_client.getWells(id) )
			break
		case "plate":
			processKVP( user_client, user_client.getPlates(id))
			break
		case "screen":
			processKVP( user_client, user_client.getScreens(id))
			break
		}
		
	} finally{
		user_client.disconnect()
		println "Disonnected "+host
	}
	
	println "Processing of key-values for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * Print all tags belogning to the current OMERO object
 * 
 * inputs
 * 		user_client : OMERO client
 * 		wpr : OMERO object wrapper (image, dataset, project, well, plate, screen)
 * 
 * */
def processKVP(user_client, repository_wpr){

	// get the current key-value pairs
	List<List<NamedValue>> keyValues = repository_wpr.getMapAnnotations(user_client).stream()
																	   .map(MapAnnotationWrapper::getContent)
																	   .toList()
	for(int i = 0; i< keyValues.size();i++){
		println "KeyValue group n° "+(i+1)
		keyValues.get(i).each{
			println "Key : "+it.name+" ; Value : "+it.value
		}
		println ""
	}
}


/*
 * imports  
 */

import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import fr.igred.omero.meta.*
import omero.gateway.model.DatasetData;
import omero.gateway.model.TagAnnotationData;
import omero.model.NamedValue
import ij.*
import ij.plugin.*
import ij.gui.PointRoi
import java.io.*;