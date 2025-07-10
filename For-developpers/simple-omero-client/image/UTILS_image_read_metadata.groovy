#@String(label="Host", value="omero-server.epfl.ch", persist=true) host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id
#@Boolean(label="Parent containers", value=true) printParent
#@Boolean(label="Sizes & pixels info", value=true) printSizes
#@Boolean(label="Channels", value=true) printChannels
#@Boolean(label="ROIs", value=true) printROIs
#@Boolean(label="Hardware", value=true) printHardware

/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO. 
 * User can specify the ID of an "image","dataset","project","well","plate","screen"
 * The selected object is then imported in FIJI
 * 
 * == INPUTS ==
 *  - host
 *  - port
 *  - credentials 
 *  - id
 *  - object type
 *  - display imported image or not
 * 
 * == OUTPUTS ==
 *  - open the image defined by id (or all images one after another from the dataset/project/... defined by id)
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.9.1 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by romain guiet & Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 04.07.2022
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
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */

// Connection to server
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "\nConnected to "+host
	
	try{
	
		processImage(user_client, user_client.getImage(id))
		println "Reading metadata on image, id "+id+": DONE !"
		
	} finally{
		user_client.disconnect()
		println "Disonnected from "+host
	}
	
}else{
	println "Not able to connect to "+host
}


/**
 * Display image metadata and import the image in Fiji
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def processImage(user_client, image_wpr){
	// clear Fiji env
	IJ.run("Close All", "");
	
	// Print image information
	println "\nImage infos"
	println ("Image_name : "+image_wpr.getName() + " / id : "+ image_wpr.getId())
	
	if(printParent){
		def dataset_wpr_list = image_wpr.getDatasets(user_client)
	
		// if the image is part of a dataset
		if(!dataset_wpr_list.isEmpty()){
			dataset_wpr_list.each{println("dataset_name : "+it.getName()+" / id : "+it.getId())};
			image_wpr.getProjects(user_client).each{println("Project_name : "+it.getName()+" / id : "+it.getId())};
		}
		
		// if the image is part of a plate
		else {
			def well_wpr = image_wpr.getWells(user_client).get(0)
			println ("Well_name : "+well_wpr.getName() +" / id : "+ well_wpr.getId())
			
			def plate_wpr = image_wpr.getPlates(user_client).get(0)
			println ("plate_name : "+plate_wpr.getName() + " / id : "+ plate_wpr.getId())
	
			def screen_wpr = image_wpr.getScreens(user_client).get(0)
			println ("screen_name : "+screen_wpr.getName() + " / id : "+ screen_wpr.getId())
		}
	}
	// print image metadata
	getImageMetadata(user_client, image_wpr)
	
	// print microscope metadata
	if(printHardware) getHardwareMetadata(user_client, image_wpr)
}


/**
 * Display image metadata and characteristics (size, channels, ROIs)
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def getImageMetadata(user_client, image_wpr){
	
	if(printSizes){
		def bounds = image_wpr.getPixels().getBounds(null, null, null, null, null);
	    println "Image width : "+bounds.getSize().getX() +" pixels"
	    println "Image height : "+bounds.getSize().getY() +" pixels"
	    println "Number of channels : "+bounds.getSize().getC()
	    println "Number of slices : "+bounds.getSize().getZ()
	    println "Number of frames : "+bounds.getSize().getT()
	
	    print "Pixel size in x ("+image_wpr.getPixels().getPixelSizeX().getValue().round(3)+" "+image_wpr.getPixels().getPixelSizeX().getUnit()+
	    "), y ("+image_wpr.getPixels().getPixelSizeY().getValue().round(3)+" "+image_wpr.getPixels().getPixelSizeY().getUnit()+") "
	    
	    if(bounds.getSize().getZ() > 1)
	    	print "and z"+image_wpr.getPixels().getPixelSizeZ().getValue().round(3)+" "+image_wpr.getPixels().getPixelSizeZ().getUnit()+"\n"
	    else
	    	print"and z = 0 \n"
	    
	    println "Pixel type : " +image_wpr.getPixels().getPixelType()
	    println "Time step : NOT POSSIBLE TO CATCH FROM OMERO DIRECTLY. Should be sth like imageWrapper.getPixels().getTimeIncrement().getValue()(and .getUnit())" 
	}
    
    if(printChannels){
	    def NANOMETER = 15
	    println "\nChannel information"
	    user_client.getMetadata().getChannelData(user_client.getCtx(), image_wpr.getId()).each{println "Name : "+it.getName()
	    																					   println "Emission lambda = "+it.getEmissionWavelength(UnitsLength.valueOf(NANOMETER))
	    																					   println "Excitation lambda = "+it.getExcitationWavelength(UnitsLength.valueOf(NANOMETER))
	    																					   println "Illumination mode = "+it.getIllumination()
	    																					   println "Acquisition mode = "+it.getMode()+"\n"} // more method available like it.getPinholeSize(); it.getNDFilter(); it.getFluor()
	}
	
	if(printROIs){
	    println "Number of ROIs on the image : " + image_wpr.getROIs(user_client).size()
	    image_wpr.getROIs(user_client).each{
	   		println "Shape count per ROI : "+it.asROIData().getShapeCount()+"-->"+it.getShapes()
	    }
	}
}


/**
 * Display hardware metadata and characteristics (microscope, environnemnt conditions)
 * 
 * inputs
 * 		user_client : OMERO client
 * 		image_wpr : OMERO image
 * 
 * */
def getHardwareMetadata(user_client, image_wpr){
		
	// see here https://forum.image.sc/t/metadata-access-with-omero-matlab-language-bindings-fails/37719/4
	// and here https://forum.image.sc/t/troubles-to-access-microscope-info-from-image-metadata-in-omero-simple-client/67806
	// to find more metatdata
	
	def MICROMETER = 14
	def CELSUIS = 1
	ImageAcquisitionData imgData = user_client.getMetadata().getImageAcquisitionData(user_client.getCtx(), image_wpr.getId())
	println "\nHardware settings"
	if(imgData.getObjective())
		println "Objective magnification : " + imgData.getObjective().getNominalMagnification() // all other functions are inside ObjectiveData.class, accessible via imgData.getObjective()....
	println "Correction collar : " + imgData.getCorrectionCollar()
	println "Microscope stage position X = " + imgData.getPositionX(UnitsLength.valueOf(MICROMETER))+", Y = "+ // Be carefull => something goes wrong for z-stack.
	imgData.getPositionY(UnitsLength.valueOf(MICROMETER))+", Z = "+
	imgData.getPositionZ(UnitsLength.valueOf(MICROMETER))
	println "Temperature (°C) :"+imgData.getTemperature(UnitsTemperature.valueOf(CELSUIS))
	println "Air Pressure (?) : " + imgData.getAirPressure(null)
	println "Humidity (?) : " + imgData.getHumidity()
	println "CO2 (%) : " + imgData.getCo2Percent()
	println "Medium : " + imgData.getMedium() + "; refractive index of "+imgData.getRefractiveIndex()
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