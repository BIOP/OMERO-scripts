#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type



/**
 * Main. Connect to OMERO, delete all tables from the specified object and disconnect from OMERO
 * 
 */
 
// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host +"\n"
	
	try{
		switch (object_type){
		case "image":	
			processTable( user_client, user_client.getImage(id) )
			break	
		case "dataset":
			processTable( user_client, user_client.getDataset(id) )
			break
		case "project":
			processTable( user_client, user_client.getProject(id) )
			break
		case "well":
			processTable( user_client, user_client.getWells(id) )
			break
		case "plate":
			processTable( user_client, user_client.getPlates(id))
			break
		case "screen":
			processTable( user_client, user_client.getScreens(id))
			break
		}
		
	} finally{
		user_client.disconnect()
		println "\n Disonnected "+host
	}
	
	println "Deletion of OMERO.tables for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}




/**
 * Delete all the tables attached to the object
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processTable(user_client, repository_wpr){
	
	def table_wpr_list = repository_wpr.getTables(user_client)
	
	table_wpr_list.each{table_wpr->
		// check that user is owner
		if  (repository_wpr.getOwner().getId() == user_client.getUser().getId() ){
			println table_wpr.getName() + " will be deleted"
			user_client.deleteFile(table_wpr.getId())
		}
		else
			println table_wpr.getName() + " will NOT be deleted"
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
import omero.model.NamedValue
import ij.*
import ij.plugin.*
import ij.gui.*
import ij.gui.PointRoi
import ij.measure.ResultsTable
import java.io.*;
