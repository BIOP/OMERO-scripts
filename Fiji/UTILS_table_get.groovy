#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type


/**
 * Main. Connect to OMERO, get a table and disconnect from OMERO
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
		println "Disonnected "+host
	}
	
	println "Download table from OMERO for "+object_type+ " "+id+" : DONE !"
	return
	
}else{
	println "Not able to connect to "+host
}




/**
 *Get an OMERO.table to display it on ResultsTable on imageJ
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processTable(user_client, repository_wpr){

	// get the OMERO.table
	List<TableWrapper> ListTableW = repository_wpr.getTables(user_client)
	
	List<String> previousNames = new ArrayList<>()
	def cnt = 1
	ListTableW.each{tbl->
		ResultsTable rt = new ResultsTable()
		
		def data = tbl.getData()
		def nbCol = tbl.getColumnCount()
		def nbRow = tbl.getRowCount()
		
		// build the ResultsTable
		if(nbCol > 2){ // if we have more than a label to display
			
			for(i = 0; i < nbRow; i++){
				rt.incrementCounter()
				rt.addLabel(data[1][i])
				for(j = 2 ; j < nbCol; j++){
					rt.setValue(tbl.getColumnName(j), i, data[j][i])
				}
			}
		}
		rt.updateResults()
		
		// change the table name if the some OMERO.tables have the same name
		if(previousNames.find{it.equals(tbl.getName())}){
			rt.show(tbl.getName()+"_"+cnt)
			cnt++
		}
		else
			rt.show(tbl.getName())
		
		previousNames.add(rt.getTitle())
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
