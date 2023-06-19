#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="ID", value=119273) id
#@String(label="Object", choices={"image","dataset","project","well","plate","screen"}) object_type

/* 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 * 
 * == OUTPUTS ==
 *  - Create a new OMERO table
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
 * 01.09.2022
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
 * - 2023.06.19 : Remove unnecessary imports
 */

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
	println "\nConnected to "+host
	
	try{
		switch (object_type){
			case "image":	
				processTable(user_client, user_client.getImage(id))
				break	
			case "dataset":
				processTable(user_client, user_client.getDataset(id))
				break
			case "project":
				processTable(user_client, user_client.getProject(id))
				break
			case "well":
				processTable(user_client, user_client.getWells(id))
				break
			case "plate":
				processTable(user_client, user_client.getPlates(id))
				break
			case "screen":
				processTable(user_client, user_client.getScreens(id))
				break
		}
		println "Adding table for "+object_type+ " "+id+" : DONE !"
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}

}else{
	println "Not able to connect to "+host
}




/**
 *Add a new OMERO.table as an attachment
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def processTable(user_client, repository_wpr){
	// build an example of ImageJ ResultsTable
	def rt = buildExampleResultsTable()
	
	// create a new table
	List<Roi> rois =  new ArrayList<>(0)
 	def table_wpr = new TableWrapper(user_client, rt , repository_wpr.getId(), rois)
	
	// upload the table on OMERO
 	table_wpr.setName(repository_wpr.getName()+"_Table")
	repository_wpr.addTable(user_client, table_wpr)
	println "Upload value to table"
}



/**
 * Build a small dummy ResultTable
 * 
 * */
def buildExampleResultsTable(){
	def raw_end_rt = new ResultsTable()
	raw_end_rt.incrementCounter()
	raw_end_rt.addLabel("Image_name")
	raw_end_rt.setValue("field 1", 0, 1.0)
	raw_end_rt.setValue("field 2", 0, 2.0)
	raw_end_rt.setValue("field 3", 0, 3.0)
	raw_end_rt.setValue("field 4", 0, 4.0)								 								 									
	raw_end_rt.updateResults()
	raw_end_rt.show("Example_table")
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
