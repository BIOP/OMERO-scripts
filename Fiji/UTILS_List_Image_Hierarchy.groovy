#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@File(label="Output folder", style="directory") outputFolder
#@Boolean(label="Save as key-values") writeAsKVP


/* = CODE DESCRIPTION =
 * - This is a template to interact with OMERO. 
 * - User can specify the ID of an "image","dataset","project","well","plate","screen"
 * - From the user choice, the code lists all the images contained in the object
 * - It generate a csv file with the list of all images (name + id) with their parent folder name (dataset/project or well/plate/screen)
 * - Optionnally, it also saves parent folder name as key-values on OMERO.
 * 
 *                                          **** BE CAREFUL *******
 * It can happen that image/dataset/project names contain comas. This is the case for all images coming from a plate and maybe for some of your images.
 * To be csv-reading compatible, all comas are replaced by semi-columns.
 * To recover the correct name, open the csv file in excel. Select the column with image names.
 * Go on Home->Search and Find->Replace. Replace ";" by "," and save.
 * We strongly suggest you to remove all comas from your image names on OMERO (if possible) before running this script.
 * 
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 *  - output folder where to save the csv file
 *  - display imported image or not
 * 
 * == OUTPUTS ==
 *  - create a csv file with the list of images contained inside the specfied object_type
 *  - key values on OMERO
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.2 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 22.08.2022
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
 * Main.
 * 
 */

// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

data_list = new ArrayList()

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
		
		makeCSVFileWithObjectList(data_list)
		println "Listing images in "+object_type+", id "+id+": DONE !\n"
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
}else{
	println "Not able to connect to "+host
}


/**
 * List the full arborescence of an image in a csv format
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def processImage(user_client, image_wpr){
	
	def dataset_wpr_list = image_wpr.getDatasets(user_client)

	// if the image is part of a dataset
	if(!dataset_wpr_list.isEmpty()){
		def dataset_wpr =  dataset_wpr_list.get(0)
		def project_wpr = image_wpr.getProjects(user_client).get(0)
		data_list.add(image_wpr.getName().replaceAll(",",";") + ","+ image_wpr.getId() + ","+ dataset_wpr.getName().replaceAll(",",";") + ","+  project_wpr.getName().replaceAll(",",";")+"\n")
		
		// add key value pairs
		if(writeAsKVP){
			List<NamedValue> keyValues = new ArrayList()
	   		keyValues.add(new NamedValue("Dataset", dataset_wpr.getName())) 
	   		keyValues.add(new NamedValue("Project", project_wpr.getName())) 
			addKeyValuetoOMERO(user_client, image_wpr, keyValues)
		}
	}
	
	// if the image is part of a plate
	else {
		def well_wpr =  image_wpr.getWells(user_client).get(0)
		def plate_wpr = image_wpr.getPlates(user_client).get(0)
		def screen_wpr = image_wpr.getScreens(user_client).get(0)
		data_list.add(image_wpr.getName().replaceAll(",",";") + ","+ image_wpr.getId() + ","+ well_wpr.getName().replaceAll(",",";") + ","+ plate_wpr.getName().replaceAll(",",";") + ","+ screen_wpr.getName().replaceAll(",",";")+"\n")
	
		// add key-value pairs
		if(writeAsKVP){
			List<NamedValue> keyValues = new ArrayList()
	   		keyValues.add(new NamedValue("Well", well_wpr.getName())) 
	   		keyValues.add(new NamedValue("Plate", plate_wpr.getName())) 
	   		keyValues.add(new NamedValue("Screen", screen_wpr.getName())) 
			addKeyValuetoOMERO(user_client, image_wpr, keyValues)
		}
	}
	
}



/**
 * Add the key value to OMERO attach to the current repository wrapper
 * 
 * inputs
 * 		user_client : OMERO client
 * 		repository_wpr : OMERO repository object (image, dataset, project, well, plate, screen)
 * 
 * */
def addKeyValuetoOMERO(user_client, repository_wpr, keyValues){
	MapAnnotationWrapper newKeyValues = new MapAnnotationWrapper(keyValues)
	newKeyValues.setNameSpace("openmicroscopy.org/omero/client/mapAnnotation")
	repository_wpr.addMapAnnotation(user_client, newKeyValues)
}



/**
 * Create a csv file 
 * 
 * input	
 * 		data_list : list of comma-separated strings
 */
def makeCSVFileWithObjectList(data_list){
	String path = outputFolder.getAbsolutePath() + "\\" + "Images_in_" + object_type + "_"+id+".csv"
	File file = new File(path);
	
	if(file.exists()){
		int i = 1
		do{	
			path = outputFolder.getAbsolutePath() + "\\" + "Images_in_" + object_type + "_"+id+"_"+i+".csv"
			file = new File(path);
			i++
			
		}while(file.exists())
	}
	println "Create a csv file in : "+ path
    FileWriter writer = new FileWriter(file)
    
    if(object_type == "well" || object_type == "plate" || object_type == "screen")
    	writer.append("image,image_id,well,plate,screen\n")
    else
    	writer.append("image,image_id,dataset,project\n")
    	
    data_list.each { d ->
    	writer.append(d)
    }

	writer.close();
}



/**
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processDataset( user_client, dataset_wpr ){
	dataset_wpr.getImages(user_client).each{ img_wpr ->
		processImage(user_client , img_wpr)
	}
}


/**
 * Import all images from a dataset in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		dataset_wpr : OMERO dataset
 * 
 * */
def processProject( user_client, project_wpr ){
	project_wpr.getDatasets().each{ dataset_wpr ->
		processDataset(user_client , dataset_wpr)
	}
}


/**
 * Import all images from a well in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		well_wpr_list : OMERO wells
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
 * Import all images from a plate in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		plate_wpr_list : OMERO plates
 * 
 * */
def processPlate(user_client, plate_wpr_list){
	plate_wpr_list.each{ plate_wpr ->	
		processWell(user_client, plate_wpr.getWells(user_client))
	} 
}


/**
 * Import all images from a screen in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		screen_wpr_List : OMERO screens
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
import fr.igred.omero.annotations.*
import omero.model.NamedValue
import java.io.FileWriter
import java.io.File