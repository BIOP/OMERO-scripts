#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type
#@String(label="Key", value = "Key") key
#@String(label="Value", value = "Value") value



/**
 * Main. Connect to OMERO, add a key-value to the last existant key-values group and disconnect from OMERO
 * 
 */
 
// Connection to server
Client user_client = new Client()
host = "omero-server.epfl.ch"
port = 4064

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
	
	println "Adding a Key-value pairs for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * Get existing key values and add a new one to the last key-values
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processKVP(user_client, repository_wpr){
	
	// get the current key-value pairs
	List<List<NamedValue>> keyValues = repository_wpr.getMapAnnotations(user_client).stream()
																	   .map(MapAnnotationWrapper::getContent)
																	   .toList()
																	   
	List keyValuesPreviousId = repository_wpr.getMapAnnotations(user_client).stream()
																	 .map(MapAnnotationWrapper::getId)
																	 .toList()
	
	// add the new key values
	if(keyValues){
		def keyValueId = keyValues.size() - 1
		keyValues.get(keyValueId).add(new NamedValue(key, value))
	}
	else 
		keyValues.add(new NamedValue(key, value))
	
	keyValues.each{addKeyValuetoOMERO(user_client, repository_wpr, it)}
	
	// delete previous keyValue pairs if exists
	if(!keyValuesPreviousId.isEmpty())
		keyValuesPreviousId.each{user_client.deleteFile(it)}

}

/**
 * Add the key value to OMERO attach to the current repository wrapper
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def addKeyValuetoOMERO(user_client, repository_wpr, keyValues){
	MapAnnotationWrapper newKeyValues = new MapAnnotationWrapper(keyValues)
	newKeyValues.setNameSpace("openmicroscopy.org/omero/client/mapAnnotation")
	repository_wpr.addMapAnnotation(user_client, newKeyValues)
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
import omero.model.NamedValue
import ij.*
import ij.plugin.*
import ij.gui.PointRoi
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.StringTokenizer;
