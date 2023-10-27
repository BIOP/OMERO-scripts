#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Dataset ID", value=1945) id
#@String(label="Table name" , value="ResultsTable") tableName
#@String (choices={"OMERO table", "CSV file"}, style="radioButtonHorizontal") fileChoice
#@Boolean(label="Delete existing Tables") isDeleteExistingTables
#@Boolean(label="Send Measurements to OMERO") isSendNewMeasurements



/* = CODE DESCRIPTION =
 * User can specify the ID of a "dataset" containing images with OMERO.tables.
 * A new table is generated by summarizing all image tables.
 * The new table is attached to the OMERO dataset, as well as the corresponding OMERO.parade compatible csv file.
 * 
 * To be compatible with omero.table, the csv file at the dataset level must contain a column with image name AND a column with image ids.
 * 
 * 
 * == INPUTS ==
 *  - credentials 
 *  - dataset id 
 *  - Table name to retrieve the correct table at the image level
 *  - Choose format of file to retrieve (csv or omero.table)
 *  - Path to a location for tmp files (that are deleted at the end of the script)
 *  - choices to delete existing or send new measurements.
 * 
 * == OUTPUTS ==
 *  - Generate a table and csv file with summary results of all images including in the dataset
 *  - Attach the table and the csv file to the current dataset on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.14.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * version : v1.5
 * 10.10.2022
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
 * 
 * == HISTORY ==
 * - 2023.06.19 : Remove unnecessary imports --v1.1
 * - 2023-06-29 : Delete table with only one API call + move to simple-omero-client 5.14.0 --v1.2
 * - 2023-10-04 : Fix bug when deleting tables if there is not table to delete --v1.3
 * - 2023-10-04 : Fix bug on counting the number of positive cells in each channel --v1.3
 * - 2023-10-04 : Rename script to "DL_Image_Group_Image_Table_To_Dataset_Table_For_HeLa" --v1.3
 * - 2023-10-16 : Replace user temporary folder input by ImageJ home directory --v1.4
 * - 2023.10.27 : Add choice between OMERO table and CSV file --v1.5
 * - 2023.10.27 : Also delete CSV files --v1.5
 */



/**
 * Main. 
 * Connect to OMERO, 
 * get the specified dataset, 
 * find each table in the child images that correspond to the tableName, 
 * process images tables, build a dataset table, 
 * send the dataset table on the specified dataset and 
 * disconnect from OMERO
 * 
 */

IJ.run("Close All", "");

// Connection to server
host = "omero-poc.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "\nConnected to "+host

	try{
		// get the selected dataset
		def dataset_wpr = user_client.getDataset(id)
		
		// get tables attached to the current dataset
		List<ImageWrapper> img_wpr_list = dataset_wpr.getImages(user_client)
	
		// initialize variables
		TableWrapper dataset_table_wpr = null;
		ResultsTable rt_image = new ResultsTable()
		ResultsTable rt_dataset = new ResultsTable()
	
		boolean isCSV = false
		img_wpr_list.each{img_wpr->	
			rt_image.reset()
			rt_dataset.reset()
	
			if(fileChoice.equals("OMERO table")){
				List<TableWrapper> table_wpr_list = img_wpr.getTables(user_client)
				rt_image = convertTableToResultsTable(table_wpr_list, tableName)
			}
			else{
				isCSV = true
				List<FileAnnotationWrapper> file_List = img_wpr.getFileAnnotations(user_client).findAll{it.getFileName().endsWith(".csv")}
				rt_image = convertCSVToResultsTable(user_client, file_List, tableName)
			}

			// build the dataset table
			if(rt_image.size() > 0){
				println "Process image "+img_wpr.getName()+", id: "+ img_wpr.getId()
				
				rt_dataset = buildDatasetResultsTable(rt_image, rt_dataset, img_wpr, isCSV)
	
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
				def table_to_delete = dataset_wpr.getTables(user_client).findAll{it.getName().contains(tableName)}
				if(table_to_delete != null && !table_to_delete.isEmpty()){
					user_client.deleteTables(table_to_delete)
				}else{
					println "WARNING : No table to delete"
				}
				
				println "Deleting existing CSV Files"
				def files_to_delete = dataset_wpr.getFileAnnotations(user_client).findAll{it.getFileName().endsWith(".csv") && it.getFileName().contains(tableName)}
				if(files_to_delete != null && !files_to_delete.isEmpty()){
					user_client.delete((Collection<GenericObjectWrapper<?>>)files_to_delete)
				}else{
					println "WARNING : No csv files to delete"
				}
			}
	
			// send the dataset table on OMERO
			if (isSendNewMeasurements){
				print "Upload table to OMERO"
				dataset_table_wpr.setName(dataset_wpr.getName()+"_"+tableName)
				dataset_wpr.addTable(user_client, dataset_table_wpr)
				println " : Done"
				
				// attach the corresponding csv file
				print "Upload csv file to OMERO"
				ResultsTable rt = convertTableToResultsTable(dataset_table_wpr)
				def temp_folder =  Prefs.getHomeDir()
				uploadResultsTable(user_client, dataset_wpr, rt, temp_folder)
			}
		}
		
		println "processing of dataset, id "+id+": DONE !"

	} finally{
		user_client.disconnect()
		println "Disconnect from "+host
	}

} else {
	println "Not able to connect to "+host
}

/**
 * Filter a list to tableWrappers and convert the right table to an ImageJ ResultstTable
 */
def convertTableToResultsTable(tableList, tableName){
	TableWrapper table_wpr = tableList.find{it.getName().contains(tableName)}
	return (table_wpr == null ? new ResultsTable() : convertTableToResultsTable(table_wpr))
}


/**
 * Build the image ResultTable from an OMERO table
 */
def convertTableToResultsTable(table_wpr){
	ResultsTable rt = new ResultsTable()
	
	def data = table_wpr.getData()
	def nbCol = table_wpr.getColumnCount()
	def nbRow = table_wpr.getRowCount()
	
	// build the ResultsTable
	if(nbCol > 2){ // if we have more than a label to display
		
		for(i = 0; i < nbRow; i++){
			rt.incrementCounter()
			// add the image IDs at the end of the table to be compatible with omero.parade
			rt.setValue(table_wpr.getColumnName(0), i, data[0][i].getId())  // this is very important to get the image ids and be omero.parade compatible
			for(j = 1 ; j < nbCol; j++){
				rt.setValue(table_wpr.getColumnName(j), i, data[j][i])
			}
		}
	}
	
	return rt
}


/**
 * Build the image ResultTable from a CSV file 
 */
def convertCSVToResultsTable(user_client, fileList, tableName){
	// filter files corresponding to the specified name
	FileAnnotationWrapper fileWpr = fileList.find{it.getFileName().contains(tableName)}
	ResultsTable rt_image = new ResultsTable()
	
	if(fileWpr != null){
		def temp_folder =  Prefs.getHomeDir() + "table.csv"
		File originalFile = null
		try{
			// download the file from the omero server
			originalFile = fileWpr.getFile(user_client, temp_folder)
			if(originalFile.exists()){
				// convert the csv file to ResultsTable
				rt_image = ResultsTable.open(originalFile.getAbsolutePath())
			}
			
		}finally{
			// delete the downloaded file
			if(originalFile != null)
				originalFile.delete()
		}
	}
	return (rt_image == null ? new ResultsTable() : rt_image)
}



/**
  * upload resultsTable as csv file
  * 
  */
def uploadResultsTable(user_client, repository_wpr, rt, temp_folder){

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
			println " : FAILED : no csv file were imported"
		
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
 * 		rt_image : Image ResultsTable
 * 		rt_dataset : Dataset ResultsTable
 * 		img_wpr : OMERO image
 * 
 * */
def buildDatasetResultsTable(rt_image, rt_dataset, img_wpr, isCSV){
	def shift = 0
	if(isCSV)
		shift = -2
	
	rt_dataset.incrementCounter()
	rt_dataset.setValue("Image Name", 0, img_wpr.getName())

	// compute the number of cells
	double one_channel_size = rt_image.size()/3

	rt_dataset.setValue("ROI count", 0, one_channel_size)		

	// compute the total and average cell area
	def total_area_list = rt_image.getColumn(2 + shift)
	double total_area = 0
	for(int i = 0; i < one_channel_size; i++){
		total_area += total_area_list[i]
	}

	rt_dataset.setValue("Total_area", 0, total_area)
	rt_dataset.setValue("Average_area", 0, total_area/one_channel_size)		

	// compute the average cell perimeter
	def perimeter_list = rt_image.getColumn(10 + shift)
	double perimeter = 0
	for(int i = 0; i < one_channel_size; i++){
		perimeter += perimeter_list[i]
	}

	rt_dataset.setValue("Average_perimeter", 0, perimeter/one_channel_size)		
	
	// count the number of positive cells for each channel.
	// cells are considered positive if their mean intensity is larger than T
	def mean_intensity_list = rt_image.getColumn(3 + shift)

	long T = 25
	long posCellCh1 = 0
	for(int i = 0; i < one_channel_size; i++){
		if(mean_intensity_list[i] > T)
			posCellCh1 += 1
	}

	long posCellCh2 = 0
	for(int i = one_channel_size; i < 2*one_channel_size; i++){
		if(mean_intensity_list[i] > T)
			posCellCh2 += 1
	}

	long posCellCh3 = 0 								 												 								 														 												 								 										
	for(int i = 2*one_channel_size; i < 3*one_channel_size; i++){
		if(mean_intensity_list[i] > T)
			posCellCh3 += 1
	}		

	rt_dataset.setValue("ch1_Threshold", 0, T)
	rt_dataset.setValue("ch2_Threshold", 0, T)
	rt_dataset.setValue("ch3_Threshold", 0, T)
	rt_dataset.setValue("PosCellCh1", 0, posCellCh1)
	rt_dataset.setValue("PosCellCh2", 0, posCellCh2)
	rt_dataset.setValue("PosCellCh3", 0, posCellCh3)
	
						 												 								 														 												 								 									
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
import ij.gui.Roi
import ij.measure.ResultsTable