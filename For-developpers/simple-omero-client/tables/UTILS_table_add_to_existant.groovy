#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id


/* 
 * Add new lines to an existing OMERO table, or create a new table if the table is empty.
 *  
 *  
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2022.09.01
 * Version: 1.0.1
 * 
 * -----------------------------------------------------------------------------
 * Copyright (c) 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)
 * All rights reserved.
 * 
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
 *    in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * -----------------------------------------------------------------------------
 * 
 * History
 * - 2023.06.19 : Remove unnecessary imports
 * - 2026.08.13 : Automatically switch group if the object is not coming from the default one -v1.0.1
 */


/**
 * Main. Connect to OMERO, add a table and disconnect from OMERO
 * 
 */
 
// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
groupId = -1

if (user_client.isConnected()){
	println "Connected to "+host
	
	try{
		switch (object_type){
			case "image":	
				if(groupId < 0) groupId = checkAndSwitchGroup(user_client, "ImageData", id)
				processTable(user_client, user_client.getImage(id))
				break	
			case "dataset":
				if(groupId < 0) groupId = checkAndSwitchGroup(user_client, "DatasetData", id)
				processTable(user_client, user_client.getDataset(id))
				break
			case "project":
				if(groupId < 0) groupId = checkAndSwitchGroup(user_client, "ProjectData", id)
				processTable(user_client, user_client.getProject(id))
				break
			case "well":
				if(groupId < 0) groupId = checkAndSwitchGroup(user_client, "WellData", id)
				processTable(user_client, user_client.getWells(id))
				break
			case "plate":
				if(groupId < 0) groupId = checkAndSwitchGroup(user_client, "PlateData", id)
				processTable(user_client, user_client.getPlate(id))
				break
			case "screen":
				if(groupId < 0) groupId = checkAndSwitchGroup(user_client, "ScreenData", id)
				processTable(user_client, user_client.getScreens(id))
				break
		}
		println "Adding table for "+object_type+ " "+id+" : DONE !"
		
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host
	}
}else{
	println "Not able to connect to "+host
}
return


def checkAndSwitchGroup(user_client, dataType, dataId){
    // get the group ID and switch context to that group
    def img = user_client.getBrowseFacility().findObject(user_client.getCtx(), dataType, dataId, true);
    def groupId = img.getGroupId();

    if(groupId > 0) {
        if (user_client.getCurrentGroupId() != groupId){
        	println "Switching group from "+user_client.getGroup(user_client.getCurrentGroupId()).getName()+" to "+user_client.getGroup(groupId).getName()
            user_client.switchGroup(groupId);
        }
    }
    return groupId
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
		user_client.deleteFile(previousTable_ID)
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
import fr.igred.omero.annotations.*
import ij.*
import ij.gui.*
import ij.measure.ResultsTable