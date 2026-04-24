#@String (visibility=MESSAGE, value="OMERO connection", required=false) msg0
#@String(label="Server name", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String (visibility=MESSAGE, value="Setup object to process", required=false) msg1
#@String(label="Object to process", choices={"dataset","project"}) object_type
#@Long(label="Object ID", value=119273) id
#@String(label="Table name" , value="ResultsTable") TABLE_NAME
#@String (choices={"OMERO table", "CSV file"}, style="radioButtonHorizontal") fileChoice
#@Boolean(label="Delete existing Tables", value=true) isDeleteExistingTables
#@String (visibility=MESSAGE, value="Output", required=false) msg4
#@Boolean(label="Send Measurements to OMERO", value=true) isSendNewMeasurements


/* Code description
 *  
 * Concatenate, for each image:
 *     - the content of the OMERO.table
 * into a summary table (one row = one ROI). The final table is attached to the parent 
 * container (dataset or project) as a CSV file and an OMERO.table.
 * 
 * 
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2023.11.06
 * Version: 2.0.0
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
 * - 2026.04.15: Update the way to group table -v2.0.0
 */


IJ.run("Close All", "");

CSV_SEPARATOR = ","
ROW_SEPARATOR = "\n"

rowCount = 0
colCount = 0
headerType = new HashMap<>()

// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host

	try{
		def resultsList = new ArrayList<>()
		def repositoryWrapper 
		switch (object_type){
			case "dataset":
				repositoryWrapper = user_client.getDataset(id)
				def imageTableContentList = processDataset(user_client, repositoryWrapper)
				if(!imageTableContentList.isEmpty()) resultsList.addAll(imageTableContentList)
				break
			case "project":
				repositoryWrapper = user_client.getProject(id)
				def imageTableContentList = processProject(user_client, repositoryWrapper)
				if(!imageTableContentList.isEmpty()) resultsList.addAll(imageTableContentList)
				break
		}
		
		if(!resultsList.isEmpty()){
			if(repositoryWrapper){
				// delete all existing tables
				if(isDeleteExistingTables){
					println "Deleting existing OMERO-Tables"
					def table_to_delete = repositoryWrapper.getTables(user_client).findAll{it.getName().contains(TABLE_NAME)}
					if(table_to_delete != null && !table_to_delete.isEmpty()){
						user_client.deleteTables(table_to_delete)
					}else{
						println "WARNING : No table to delete"
					}
					
					println "Deleting existing CSV Files"
					def files_to_delete = repositoryWrapper.getFileAnnotations(user_client).findAll{it.getFileName().endsWith(".csv") && it.getFileName().contains(TABLE_NAME)}
					if(files_to_delete != null && !files_to_delete.isEmpty()){
						user_client.delete((Collection<GenericObjectWrapper<?>>)files_to_delete)
					}else{
						println "WARNING : No csv files to delete"
					}
				}
		
				// send the dataset table on OMERO
				if (isSendNewMeasurements){
					println "Creating summary table..."
					def tableWrapper = createTable(resultsList)
					print "Uploading table to OMERO..."
					repositoryWrapper.addTable(user_client, tableWrapper)
					println "Done"
					
					println "Creating summary CSV table..."
					def content = createCSVTable(resultsList)
					String path = System.getProperty("user.home") + File.separator + "Downloads"
					File summaryFile = writeCSVFile(path, TABLE_NAME + "-Summary", content)
					if(summaryFile != null && summaryFile.exists()){
						print "Uploading CSV table to OMERO0..."
						repositoryWrapper.addFile(user_client, summaryFile)
						summaryFile.delete()
						println "Done"
					}else{
						println "ERROR: File is not existing"
					}
				}
			}
		}
		println "Processing of "+object_type+", id "+id+": DONE !"
		
	} finally{
		user_client.disconnect()
		println "Disconnected from "+host+"\n"
	}
} else {
	println "Not able to connect to "+host+"\n"
}
return 


def processImage(user_client, image_wpr){
	
	println "Working on image '"+image_wpr.getName()+"'"
	def resultsMap = new HashMap<>()
	
	if(fileChoice.equals("OMERO table")){
		List<TableWrapper> table_wpr_list = image_wpr.getTables(user_client)
		def table_map = convertTableToMap(table_wpr_list, TABLE_NAME)
		if(!table_map.isEmpty()) resultsMap.put(image_wpr, table_map)
	}
	else{
		isCSV = true
		List<FileAnnotationWrapper> file_List = image_wpr.getFileAnnotations(user_client).findAll{it.getFileName().endsWith(".csv")}
		def table_map = convertCSVToMap(user_client, file_List, TABLE_NAME)
		if(!table_map.isEmpty()) resultsMap.put(image_wpr, table_map)
	}
	return resultsMap
}


/**
 * Filter a list to tableWrappers and convert the right table to an ImageJ ResultstTable
 */
def convertTableToMap(tableList, tableName){
	TableWrapper table_wpr = tableList.find{it.getName().contains(tableName)}
	return (table_wpr == null ? new HashMap<>() : convertTableToMap(table_wpr))
}


def getHeaderType(table_wpr){
	def headerType = new HashMap<>()
	def data = table_wpr.getData()
	def nbCol = table_wpr.getColumnCount()
	
	headerType.put(table_wpr.getColumnName(0), true)
	
	for(j = 1 ; j < nbCol; j++){
		try{
			Double.parseDouble(String.valueOf(data[j][0]))
			headerType.put(table_wpr.getColumnName(j), true)
		}catch (Exception e){
			headerType.put(table_wpr.getColumnName(j), false)
		}
	}
	return headerType
}

/**
 * Build the image ResultTable from an OMERO table
 */
def convertTableToMap(table_wpr){
	def resultsList = new ArrayList<>()
	
	if(headerType.isEmpty()){
		headerType = getHeaderType(table_wpr)
	}
	
	def data = table_wpr.getData()
	def nbCol = table_wpr.getColumnCount()
	def nbRow = table_wpr.getRowCount()
	rowCount += nbRow
	colCount = nbCol
	
	if(nbCol > 2){
		for(i = 0; i < nbRow; i++){
			def resultsMap = new LinkedHashMap<>()
			// add the image IDs at the end of the table to be compatible with omero.parade
			resultsMap.put(table_wpr.getColumnName(0), (double)data[0][i].getId())  // this is very important to get the image ids and be omero.parade compatible
			for(j = 1 ; j < nbCol; j++){
				if(headerType.get(table_wpr.getColumnName(j))){
					resultsMap.put(table_wpr.getColumnName(j), Double.parseDouble(String.valueOf(data[j][i])))
				}else{
					resultsMap.put(table_wpr.getColumnName(j), String.valueOf(data[j][i]))
					
				}
			}
			resultsList.add(resultsMap)
		}
	}
	return resultsList
}


/**
 * Build the image ResultTable from a CSV file 
 */
def convertCSVToMap(user_client, fileList, tableName){
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
	
	rt_image == null ? new ResultsTable() : rt_image
	
	def resultsList = new ArrayList<>()
	def headerList = rt_image.getHeadings() as List
	
	for(int i = 0; i < rt_image.size(); i++){
		def resultsMap = new LinkedHashMap<>()	
		for(int j = 0; j < headerList.size(); j++){
			resultsMap.put(headerList.get(j), rt_image.getStringValue(headerList.get(j), i))
		}
			
		resultsList.add(resultsMap)
	}
	return resultsList
}

/**
 * Create the OMERO.table of particle statistics
 */
def createTable(resultsList){
	def tableWrapper = new TableWrapper(colCount + 1, TABLE_NAME + "-Summary")
	tableWrapper.setRowCount(rowCount)

	def header =  resultsList.get(0).entrySet().iterator().next().getValue().get(0).keySet() as List
	Object[] rowAttributes = new Object[header.size() + 1]
	
	tableWrapper.setColumn(0, "Image", ImageData.class)
	
	for(int i = 0; i < header.size(); i++){
		if(headerType.get((header.get(i)))){
			tableWrapper.setColumn(i+1, header.get(i), Double.class)
		}else{
			tableWrapper.setColumn(i+1, header.get(i), String.class)
		}
	}

	resultsList.each{imageMap ->
		def imageWrapper = imageMap.entrySet().iterator().next().getKey()
		
		imageMap.entrySet().iterator().next().getValue().each{roiMap ->
			rowAttributes[0] = imageWrapper.asDataObject()

			//fill the row with size values
			roiMap.keySet().eachWithIndex{key, idx ->
				rowAttributes[idx+1] = roiMap.get(key)
			}
			tableWrapper.addRow(rowAttributes)
		}
	}

	return tableWrapper	
}


/**
 * Create the OMERO.table of particle statistics
 */
def createCSVTable(resultsList){
	def rowContent = new ArrayList<>()
	
	def header =  resultsList.get(0).entrySet().iterator().next().getValue().get(0).keySet() as List
	header.add(0, "Image")
	rowContent.add(header.join(CSV_SEPARATOR))
	
	resultsList.each{imageMap ->
		def imageWrapper = imageMap.entrySet().iterator().next().getKey()
		
		imageMap.entrySet().iterator().next().getValue().each{roiMap ->
			def currentRow = []
			currentRow.add(String.valueOf(imageWrapper.getId()))

			//fill the row with size values
			roiMap.keySet().eachWithIndex{key, idx ->
				currentRow.add(String.valueOf(roiMap.get(key)))
			}
			rowContent.add(currentRow.join(CSV_SEPARATOR))
		}
	}

	return rowContent.join(ROW_SEPARATOR)	
}


/**
 * Save a csv file in the given path, with the given name
 */
def writeCSVFile(path, name, fileContent){
	// create the file locally
    File file = new File(path.toString() + File.separator + name + ".csv");

    try (BufferedWriter buffer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
        buffer.write(fileContent);
	}catch(Exception e){
		throw e
	}
	
	return file
}


/**
 * process all images within a dataset
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	def resultsList = new ArrayList<>()
	dataset_wpr.getImages(user_client).each{ image_wpr ->
		def imageTableContentList = processImage(user_client , image_wpr)
		if(!imageTableContentList.isEmpty()) resultsList.add(imageTableContentList)
	}
	return resultsList
}


/**
 * process all datasets within a project
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		project_wpr : OMERO project
 * 
 * */
def processProject( user_client, project_wpr ){
	def resultsList = new ArrayList<>()
	project_wpr.getDatasets().each{ dataset_wpr ->
		def imageTableContentList = processDataset(user_client , dataset_wpr)
		if(!imageTableContentList.isEmpty()) resultsList.addAll(imageTableContentList)
	}
	return resultsList
}




/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import omero.gateway.model.*
import fr.igred.omero.annotations.*
import ij.*
import ij.gui.Roi
import ij.measure.ResultsTable
import java.nio.charset.StandardCharsets;
import java.awt.Color
import java.util.stream.Collectors