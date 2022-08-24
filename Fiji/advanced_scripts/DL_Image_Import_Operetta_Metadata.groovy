#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@Long(label="Plate ID", value=119273) id
#@File(label="CSV file of plate info", value="") csvFile


/* = CODE DESCRIPTION =
 * - This is a template to interact with OMERO. 
 * - The user enter the plate ID and give the path to his/her csv file containing the plate layout information foamtted as described here:
 * https://wiki-biop.epfl.ch/en/Image_Storage/OMERO/Importation
 * - The code reads the csv file and extracts key-values.
 * - Each of the key-value is then imported on corresponding images on OMERO
 * - Extra key-values corresponding to images arborescence are also added.
 * 
 * == INPUTS ==
 *  - credentials 
 *  - id
 *  - object type
 *  - output folder where to save the csv file
 *  - display imported image or not
 * 
 * == OUTPUTS ==
 *  - key value on OMERO
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
 */

/**
 * Main. 
 * Connect to OMERO, 
 * read csv file
 * generate key-values
 * import key-values on OMERO
 * disconnect from OMERO
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
		processPlate(user_client, user_client.getPlates(id))
		
	} finally{
		user_client.disconnect()
		println "Disonnected "+host
	}
	
	println "Listing images in plate, id "+id+": DONE !\n"
	return
	
}else{
	println "Not able to connect to "+host
}


/**
 * Add key value pairs to OMERO
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 		operettaKeyValues : key-value from operetta csv file
 * 
 * */
def processImage(user_client, image_wpr, operettaKeyValues){

	def well_wpr =  image_wpr.getWells(user_client).get(0)
	def plate_wpr = image_wpr.getPlates(user_client).get(0)
	def screen_wpr = image_wpr.getScreens(user_client).get(0)

	// add arborescence key values
	List<NamedValue> keyValues = new ArrayList()
	keyValues.add(new NamedValue("Well", well_wpr.getName())) 
	keyValues.add(new NamedValue("Plate", plate_wpr.getName())) 
	keyValues.add(new NamedValue("Screen", screen_wpr.getName())) 
	addKeyValuetoOMERO(user_client, image_wpr, keyValues)
	
	// add operetta key values
	addKeyValuetoOMERO(user_client, image_wpr, operettaKeyValues)
	
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
 * Read the given csv file. The file should be formatted as below : 
 * 	- First column : Row of the well
 * 	- Second column : Column of the well
 * 	- other columns : key-values (key on the column header and values below)
 * 	=> one row = one well + header at the begining formatted like "Row,Column,Key1,Key2,Key3,..."
 * 
 * inputs
 * 		user_client : OMERO client
 * 		well_wpr_list : OMERO wells
 * 
 * */
def processWell(user_client, well_wpr_list){		
	well_wpr_list.each{ well_wpr ->		
			
		def wellLine = ""
		// read the csv file
		def lines = csvFile.readLines()
		
		// get the header
		def header = lines[0].split(",")
		
		// find the current well in the csv file
		for(int i = 1; i<lines.size(); i++){
  			wellLine = lines[i].split(",")
  			if(wellLine[0] == well_wpr.identifier(well_wpr.getRow().intValue() + 1) && wellLine[1] == well_wpr.getColumn().intValue() + 1){
  				break
  			}
		}
		
		// create the key-values for the current well
		List<NamedValue> keyValues = new ArrayList()
		for(int i = 0; i<wellLine.size(); i++){
			keyValues.add(new NamedValue(header[i], wellLine[i])) 
		}
		
		// add key-values to images within the current well on OMERO
		well_wpr.getWellSamples().each{			
			processImage(user_client, it.getImage(), keyValues)		
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



/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import fr.igred.omero.repository.PixelsWrapper
import fr.igred.omero.annotations.*
import fr.igred.omero.meta.*
import omero.gateway.facility.MetadataFacility
import omero.gateway.facility.BrowseFacility
import omero.gateway.model.*
import omero.model.NamedValue
import omero.model.Experimenter.*
import omero.model.TagAnnotationDataI.*
import omero.gateway.model.TagAnnotationData.*
import omero.gateway.model.TagAnnotationData
import omero.gateway.model.FileAnnotationData.*
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.RatingAnnotationData.*
import omero.gateway.model.RatingAnnotationData
import omero.model.ExperimenterI.*
import omero.model.enums.UnitsLength
import omero.model.enums.UnitsTemperature
import omero.gateway.model.ImageAcquisitionData
import ij.*
import omero.RLong;
import omero.model.*;
import java.io.File