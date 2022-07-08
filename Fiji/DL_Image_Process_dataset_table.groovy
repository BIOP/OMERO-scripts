#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Dataset ID", value=119273) id
#@String(label="KeyWord to find tables" , value="key") keyword
#@Boolean(label="Delete existing Tables") isDeleteExistingTables
#@Boolean(label="Send Measurements to OMERO") isSendNewMeasurements


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO . 
 * User can specify the ID of an "image","dataset","project","well","plate","screen"
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id 
 *  - object type
 * 
 * == OUTPUTS ==
 *  - open the image defined by id (or all images one after another from the dataset/project/... defined by id)
 *  - 
 * 
 * = DEPENDENCIES =
 *  - omero_ij : https://github.com/ome/omero-insight/releases/download/v5.7.0/omero_ij-5.7.0-all.jar
 *  - simple-omero-client : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by romain guiet, EPFL - SV -PTECH - BIOP 
 * 06.04.2022
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2022
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
 */



/**
 * Main. 
 * Connect to OMERO, 
 * get the specified dataset, 
 * find each table in the child images that correspond to the keyword, 
 * process images tables, build a dataset table, 
 * send the dataset table on the specified dataset and 
 * disconnect from OMERO
 * 
 */

IJ.run("Close All", "");

// Connection to server
Client user_client = new Client()
host = "omero-server.epfl.ch"
port = 4064

user_client.connect(host, port, USERNAME, PASSWORD.toCharArray());
println "Connection to "+host+" : Success"


try{
		
	processDataset( user_client, user_client.getDataset(id) )

} finally{
	user_client.disconnect()
	println "Disconnection to "+host+", user: Success"
}

println "processing of dataset, id "+id+": DONE !"
return



/**
 * Add a summary table in the dataset. Each line of the dataset_table corresponds 
 * to an image with image global information
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset(user_client, dataset_wpr){
	// get tables attached to the current dataset
	List<ImageWrapper> img_wpr_list = dataset_wpr.getImages(user_client)
	
	// initialize variables
	TableWrapper dataset_table_wpr = null;
	ResultsTable rt_image = new ResultsTable()
	ResultsTable rt_dataset = new ResultsTable()
	
	
	img_wpr_list.each{img_wpr->
		// get the list of image tables
		List<TableWrapper> table_wpr_list = img_wpr.getTables(user_client)
		TableWrapper table_wpr
		
		// get only the table corresponding to the keyword
		for(TableWrapper t_wpr : table_wpr_list){
			if (t_wpr.getName().contains(keyword)){
				table_wpr = t_wpr
				break
			}
		}
		
		rt_image.reset()
		rt_dataset.reset()
		
		// build the dataset table
		if(table_wpr){
			println "Process image "+img_wpr.getName()+", id: "+ img_wpr.getId()
			rt_image = getResultTable(table_wpr, rt_image)
			rt_dataset = buildDatasetResultsTable(rt_image, rt_dataset, img_wpr)
			
			List<Roi> rois =  new ArrayList<>(0)
			if(dataset_table_wpr == null)
				dataset_table_wpr = new TableWrapper(user_client, rt_dataset, img_wpr.getId(), rois)
			else
				dataset_table_wpr.addRows(user_client, rt_dataset , img_wpr.getId(), rois)
			
		}
		else{
			println "There is no tables for image "+img_wpr.getName()+", id: "+ img_wpr.getId()
		}
	}
	
	if(dataset_table_wpr){
		// delete all existing tables
		if(isDeleteExistingTables){
			println "Deleting existing OMERO-Tables"
			dataset_wpr.getTables(user_client).each{ user_client.delete(it) }
		}
	
		// send the dataset table on OMERO
		if (isSendNewMeasurements){
			dataset_table_wpr.setName(dataset_wpr.getName()+"_Results_Table")
			dataset_wpr.addTable(user_client, dataset_table_wpr)
			println "Upload table to OMERO"
		}
	}
}



/**
 * Build the image ResultTable
 * 
 * inputs
 * 	 	table_wpr : OMERO image table
 * 		rt_image : Image ResultsTable
 * 
 * */
def getResultTable(table_wpr, rt_image){
		
	def data = table_wpr.getData()
	def nbCol = table_wpr.getColumnCount()
	def nbRow = table_wpr.getRowCount()
	
	// build the ResultsTable
	if(nbCol > 2){ // if we have more than a label to display
		
		for(i = 0; i < nbRow; i++){
			rt_image.incrementCounter()
			rt_image.addLabel(data[1][i])
			for(j = 2 ; j < nbCol; j++){
				rt_image.setValue(table_wpr.getColumnName(j), i, data[j][i])
			}
		}
	}
	rt_image.updateResults()
	rt_image.show("image_table")
	
	return rt_image
}



/**
 * Build the dataset ResultTable
 * 
 * inputs
 * 		rt_image : Image ResultsTable
 * 		rt_dataset : Dataset ResultsTable
 * 		img_wpr : OMERO image
 * 
 * */
def buildDatasetResultsTable(rt_image, rt_dataset, img_wpr){
	rt_dataset.incrementCounter()
	rt_dataset.addLabel(img_wpr.getName())
	rt_dataset.setValue("ROI count", 0, rt_image.size())						 								 									
	rt_dataset.updateResults()
	rt_dataset.show("dataset_table")
	return rt_dataset	 	
}



/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import ij.*
import ij.plugin.*
import ij.gui.PointRoi
import ch.epfl.biop.wrappers.cellpose.ij2commands.Cellpose_SegmentImgPlusAdvanced
import ch.epfl.biop.ij2command.*
import ij.gui.Roi
import ij.measure.ResultsTable