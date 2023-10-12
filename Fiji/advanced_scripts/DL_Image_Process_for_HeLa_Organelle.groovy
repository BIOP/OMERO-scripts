#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@String(label="Table name", value="ResultsTable") table_name
#@Boolean(label="Show images") showImages
#@Boolean(label="Delete existing ROIs") isDeleteExistingROIs
#@Boolean(label="Delete existing Tables") isDeleteExistingTables
#@Boolean(label="Send ROIs to OMERO") isSendNewROIs
#@Boolean(label="Send Measurements to OMERO") isSendNewMeasurements

#@ResultsTable rt_image
#@RoiManager rm


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO . 
 * User can specify the ID of an "image","dataset","project","well","plate","screen"
 * An image processing pipeline is applied on a dataset of images (HeLa cells). 
 * Results are then uploaded on OMERO
 * 
 * = BEFORE RUNNING =
 * - Check in "Analyze->Set Measurements..." ONLY the following checkboxes : Area, Mean gray value, Min & max gray value, Perimeter, Display label
 * 
 * = DATASET =
 *  - Downlaod HeLa cells images dataset from this zenodo repository : https://zenodo.org/record/4248921#.Ys0TM4RBybg
 *  - Import this dataset in OMERO. See the following link to know how to import data on OMERO : https://wiki-biop.epfl.ch/en/Image_Storage/OMERO/Importation
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id 
 *  - object type
 *  - table name
 *  - choices to delete existing or import new ROIs/measurements
 * 
 * == OUTPUTS ==
 *  - open the image defined by id (or all images one after another from the dataset/project/... defined by id)
 *  - Send to OMERO computed ROIs/measurement if defined so.
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client 5.14.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by romain guiet and Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * version v1.3
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
 * 
 * == HISTORY ==
 * - 2023-06-16 : Limits the number of call to the OMERO server + update the version of simple-omero-client to 5.12.3 + update documentation --v1.1
 * - 2023-06-29 : deletes tables with only one API call + move to simple-omero-client 5.14.0 --v1.2
 * - 2023-10-04 : Fix bug on counting the number of positive cells in each channel by adding new measurements --v1.3
 * - 2023-10-04 : Move from Li to Huang thresholding method --v1.3
 * - 2023-10-04 : Fix bug when deleting tables if there is not table to delete --v1.3
 */

/**
 * Main. 
 * Connect to OMERO, 
 * get the specified object (image, dataset, project, well, plate, screen), 
 * process all images contained in the specified object (segment cells and analyse detected cells in the 3 channels of each image), 
 * send ROIs of cells to OMERO
 * send the image table of measurements on OMERO and  
 * disconnect from OMERO
 * 
 */
 
IJ.run("Close All", "");
rm.reset()
rt_image.reset()

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
				processImage(user_client, user_client.getImage(id))
				break	
			case "dataset":
				processDataset(user_client, user_client.getDataset(id))
				break
			case "project":
				processProject(user_client, user_client.getProject(id))
				break
			case "well":
				processWell(user_client, user_client.getWells(id))
				break
			case "plate":
				processPlate(user_client, user_client.getPlates(id))
				break
			case "screen":
				processScreen(user_client, user_client.getScreens(id))
				break
		}
		println "Processing of "+object_type+", id "+id+": DONE !"

	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
} else {
	println "Not able to connect to "+host
}


// add the Image Processing & Analysis part here 
def ipas(imp){
	def dapiCh_imp = new Duplicator().run(imp,2,2,1,1,1,1)
	def dapiCh_imp_final = dapiCh_imp.duplicate()
	// filtering
	IJ.run(dapiCh_imp, "Median...", "radius=3");

	// thresholding
	IJ.setAutoThreshold(dapiCh_imp, "Huang dark");
	Prefs.blackBackground = true;
	IJ.run(dapiCh_imp, "Convert to Mask", "");
	
	// morphological improvements
	IJ.run(dapiCh_imp, "Open", "");
	IJ.run(dapiCh_imp, "Fill Holes", "");

	// analyze connected components
	IJ.run("Set Measurements...", "mean min centroid center display redirect=None decimal=3");
	IJ.run(dapiCh_imp, "Analyze Particles...", "clear add"); // If you clear, previous ROI are deleted

	// get Rois from Roi manager
	def rois = rm.getRoisAsArray()	
	
	// delete Roi manager
	rm.reset()
	
	def filtered_Rois = rois.findAll{ it.getStatistics().area > 50 }

	filtered_Rois.each{rm.addRoi(it)}
	int chN = imp.getNChannels()
	
	int nROI = rm.getCount();
	rm.runCommand("Associate", "true");
	
	for (int j=0; j<nROI; j++) {
	    rm.select(j);
	    IJ.run("Make Band...", "band=5");
	    rm.runCommand("Update");
	}
	
	rm.deselect();
	IJ.run("Clear Results");

	(1..chN).each{
		imp.setPosition(it,1,1)
		rm.runCommand(imp,"Measure");
	}
	
	rt_image = ResultsTable.getResultsTable("Results")
	rt_image.show("Image_Table")
}


/**
 * Manage OMERO-ImageJ connection for ROIs, image and tabes
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		img_wpr : OMERO image
 * 
 * */
def processImage(user_client, img_wpr){
	// clear Fiji env
	IJ.run("Close All", "");
	rm.reset()
	rt_image.reset()
	
	println img_wpr.getName()
	ImagePlus imp = img_wpr.toImagePlus(user_client);
	if (showImages) imp.show()
	
	// delete existing ROIs
	if(isDeleteExistingROIs){
		println "Deleting existing OMERO-ROIs"
		def roisToDelete = img_wpr.getROIs(user_client)
		if (roisToDelete != null && !roisToDelete.isEmpty())
			user_client.delete((Collection<GenericObjectWrapper<?>>)roisToDelete)
	}
	
	if(isDeleteExistingTables){
		println "Deleting existing OMERO-Tables"
		def tables_to_delete = img_wpr.getTables(user_client)
		if (tables_to_delete != null && !tables_to_delete.isEmpty())
			user_client.deleteTables(tables_to_delete)
	}

	// do the processing here 
	println "Image Processing & Analysis : Start"
	ipas(imp)
	println "Image Processing & Analysis : End"
					
	// send ROIs to Omero
	if (isSendNewROIs){
		println "New ROIs uploading to OMERO"
		def roisToUpload = ROIWrapper.fromImageJ(rm.getRoisAsArray() as List)
		img_wpr.saveROIs(user_client , roisToUpload)	
	}
	
	if (isSendNewMeasurements){
		println "New Measurement uploading to Omero"
		TableWrapper table_wpr = new TableWrapper(user_client, rt_image, img_wpr.getId(), rm.getRoisAsArray() as List)
		table_wpr.setName(img_wpr.getName()+"_"+table_name)
		img_wpr.addTable(user_client, table_wpr)
	}	
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
	def dataset_table = null;
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		processImage(user_client , img_wpr)
	}
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
	project_wpr.getDatasets().each{ dataset_wpr ->
		processDataset(user_client , dataset_wpr)
	}
}


/**
 * process all images within a well
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		well_wpr_listet_wpr :  list of OMERO wells
 * 
 * */
def processWell(user_client, well_wpr_list){		
	well_wpr_list.each{ well_wpr ->				
		well_wpr.getWellSamples().each{			
			processImage(user_client, it.getImage())		
		}
	}	
}


/**
 * process all wells within a plate
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		plate_wpr_list : List of OMERO plates
 * 
 * */
def processPlate(user_client, plate_wpr_list){
	plate_wpr_list.each{ plate_wpr ->	
		processWell(user_client, plate_wpr.getWells(user_client))
	} 
}


/**
 * process all plates within a screen
 * 
 * inputs
 * 	 	user_client : OMERO client
 * 		screen_wpr_list : List of OMERO screens
 * 
 * */
def processScreen(user_client, screen_wpr_list){
	screen_wpr_list.each{ screen_wpr ->	
		processPlate(user_client, screen_wpr.getPlates())
	} 
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
import ij.measure.ResultsTable