#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type




/**
 * Main. Connect to OMERO, add a table and disconnect from OMERO
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
	
	println "Adding table for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}



/**
 *Add a new OMEOR.table as an attachment to an existing table if this table has the same number of columns
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processTable(user_client, repository_wpr){

	// build an example of ImageJ ResultsTable
	rt = buildExampleResultsTable()
	
 	// get the tables from OMERO
 	tables = repository_wpr.getTables(user_client)
 	
 	// Find if a table already exists. If yes, keep its id
 	existingTable = false
 	if (!tables.isEmpty() ){
 		existingTable = true
 		// take the last table
 		previousTable_ID = tables[-1].getId() as Long
 		table_wpr = tables[-1]
 	}
	
	// add to the existing table the new IJ ResultsTable or create a new table if there is not any previous table in OMERO
	List<Roi> rois =  new ArrayList<>(0)

 	if (existingTable && (table_wpr.getColumnCount()-2 == rt.getLastColumn()+1)){
 		table_wpr.addRows(user_client, rt , repository_wpr.getId(), rois)
 	}else{
 		table_wpr = new TableWrapper(user_client, rt , repository_wpr.getId(), rois)
 		existingTable = false
 	}
 	
 	table_wpr.setName(repository_wpr.getName()+"_Table")
	repository_wpr.addTable(user_client, table_wpr)
	println "Upload value to table"
	
	// if a previous table already exist, delete the previous one	
	if (existingTable){
		user_client.deleteFile( previousTable_ID )
		println "Previous table deleted"
	}
	
}



/**
 * Build a small dummy ResultTable
 * 
 * */
def buildExampleResultsTable(){
	raw_end_rt = new ResultsTable()
	raw_end_rt.show("Example_Table")
	raw_end_rt.incrementCounter()
	raw_end_rt.addLabel("Image_name")
	raw_end_rt.setValue("field_uniformity", 0, 0.0)
	raw_end_rt.setValue("field_distortion", 0, 0.0)
	raw_end_rt.setValue("PCC", 0, 0.0)
	raw_end_rt.setValue("FWHM", 0, 0.0)								 								 									
	raw_end_rt.updateResults()
	return raw_end_rt
			 	
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
