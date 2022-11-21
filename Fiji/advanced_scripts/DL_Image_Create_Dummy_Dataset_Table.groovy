#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Dataset ID", value=1945) id
#@String(label="Table name") tableName
#@File(label="Folder for saving",style="directory") temp_folder


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO . 
 * User can specify the ID of a "dataset" containing images with OMERO.tables.
 * A new table is generated and attached to the specified dataset.
 * 
 * To be compatible with omero.table, the csv file at the dataset level must contain a column with image name AND a column with image ids.
 * 
 * 
 * == INPUTS ==
 *  - credentials 
 *  - dataset id 
 *  - table name to save the file with
 *  - Path to folder to save tmp data that are removed after the processing
 * 
 * == OUTPUTS ==
 *  - Generate a dummy table and its corresponding csv file
 *  - Attach the table and the csv file to the current dataset on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.1 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 12.07.2022
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
	// get the selected dataset
	def dataset_wpr = user_client.getDataset(id)

	// get tables attached to the current dataset
	List<ImageWrapper> img_wpr_list = dataset_wpr.getImages(user_client)
	
	// initialize variables
	TableWrapper dataset_table_wpr = null;
	ResultsTable rt_dataset = new ResultsTable()
	
	// loop on each image within the dataset
	img_wpr_list.each{img_wpr->
		println "Process image "+img_wpr.getName()+", id: "+ img_wpr.getId()
		
		// get the list of image tables
		List<TableWrapper> table_wpr_list = img_wpr.getTables(user_client)
		rt_dataset.reset()
		
		// build a dataset row
		rt_dataset = buildDummyDatasetResultsTable(rt_dataset, img_wpr)
		
		// build the OMERO.table
		List<Roi> rois =  new ArrayList<>(0)
		if(dataset_table_wpr == null)
			dataset_table_wpr = new TableWrapper(user_client, rt_dataset, img_wpr.getId(), rois)
		else
			dataset_table_wpr.addRows(user_client, rt_dataset , img_wpr.getId(), rois)
		
	}
	
	// attach the OMERO.table
	print "Upload table to OMERO"
	dataset_table_wpr.setName(dataset_wpr.getName()+"_"+tableName)
	dataset_wpr.addTable(user_client, dataset_table_wpr)
	println " : Done"
			
	// attach the corresponding csv file
	print "Upload csv file to OMERO"
	ResultsTable rt = getResultTable(dataset_table_wpr)
	uploadResultsTable(user_client, dataset_wpr, rt)


} finally{
	user_client.disconnect()
	println "Disconnection to "+host+", user: Success"
}

println "processing of dataset, id "+id+": DONE !"
return



/**
 * Build the image ResultTable from a TableWrapper
 * 
 * inputs
 * 	 	table_wpr : OMERO image table
 * 
 * */
def getResultTable(table_wpr){
	ResultsTable rt_image = new ResultsTable()
	def data = table_wpr.getData()
	def nbCol = table_wpr.getColumnCount()
	def nbRow = table_wpr.getRowCount()
	
	// build the ResultsTable
	if(nbCol > 2){ // if we have more than a label to display
		
		for(i = 0; i < nbRow; i++){
			rt_image.incrementCounter()
			// add the image IDs at the end of the table to be compatible with omero.parade
			rt_image.setValue(table_wpr.getColumnName(0), i, data[0][i].getId())  // this is very important to get the image ids and be omero.parade compatible
			for(j = 1 ; j < nbCol; j++){
				rt_image.setValue(table_wpr.getColumnName(j), i, data[j][i])
			}
		}
	}
	
	return rt_image
}


/**
  * upload resultsTable as csv file
  * 
  */
def uploadResultsTable(user_client, repository_wpr, rt){

	def previous_name = rt.getTitle()
	analysisimage_output_path = new File (temp_folder , repository_wpr.getName().replace(" ","_") + "_"+tableName+".csv" )
	rt.save(analysisimage_output_path.toString())
	rt.show(previous_name)
	
	try{
		def nFile = repository_wpr.getFileAnnotations(user_client).size()
		
		// Import csv on OMERO
		fileID = repository_wpr.addFile(user_client, analysisimage_output_path)
		
		// test if all csv files are imported
		if(repository_wpr.getFileAnnotations(user_client).size() == nFile + 1)
			println " : Done"
		else
			println " : FAILED no csv file were imported"
		
	} finally{
		// delete the file after upload
		analysisimage_output_path.delete()
	}
}


/**
 * Build the dataset ResultTable. You can change this method to fit yours needs
 * (more inputs, other processings...)
 * 
 * inputs
 * 		rt_dataset : Dataset ResultsTable
 * 		img_wpr : OMERO image
 * 
 * */
def buildDummyDatasetResultsTable(rt_dataset, img_wpr){
	rt_dataset.incrementCounter()
	rt_dataset.setValue("Image Name", 0, img_wpr.getName())
	
	def bounds = img_wpr.getPixels().getBounds(null, null, null, null, null);
	rt_dataset.setValue("Image width", 0, (int)bounds.getSize().getX())
	rt_dataset.setValue("Image height", 0, (int)bounds.getSize().getY())
	rt_dataset.setValue("nChannels", 0, (int)bounds.getSize().getC())
	rt_dataset.setValue("nSlices", 0, (int)bounds.getSize().getZ())
	rt_dataset.setValue("nFrames", 0,(int) bounds.getSize().getT())
						 												 								 														 												 								 									
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