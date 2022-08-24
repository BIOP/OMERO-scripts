#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type



/**
 * Main. Connect to OMERO, delete attachments and disconnect from OMERO
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
			processAttachment( user_client, user_client.getImage(id) )
			break	
		case "dataset":
			processAttachment( user_client, user_client.getDataset(id) )
			break
		case "project":
			processAttachment( user_client, user_client.getProject(id) )
			break
		case "well":
			processAttachment( user_client, user_client.getWells(id) )
			break
		case "plate":
			processAttachment( user_client, user_client.getPlates(id))
			break
		case "screen":
			processAttachment( user_client, user_client.getScreens(id))
			break
		}
		
	} finally{
		user_client.disconnect()
		println "Disonnected "+host
	}
	
	println "Processing of attachments for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * Delete all the attachment from an object
 * BE CAREFUL : you will delete the attachment, not remove the attachment from the object. Meaning that every people that use this attachment will losse it.
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processAttachment(user_client, repository_wpr){
	
	def file_wpr_list = repository_wpr.getFileAnnotations(user_client)
	
	if  (repository_wpr.getOwner().getId() == user_client.getUser().getId() ){
		file_wpr_list.each{file_wpr->
			if  (file_wpr.getOwner().getId() == user_client.getUser().getId() ){
					println file_wpr.getFileName() + " will be deleted"
					user_client.delete(file_wpr)
			}else
				println file_wpr.getFileName() + " will NOT be deleted"
		}
	}else
		println file_wpr.getName() + " will NOT be deleted"
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
