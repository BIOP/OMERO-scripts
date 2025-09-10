#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@String(label="Object to process", choices={"image","dataset","project","well","plate","screen"}) object_type
#@Long(label="Object ID", value=119273) id
#@Long(label="Resolution level", value=2) level
#@Boolean(label="Show images") showImages


/* = CODE DESCRIPTION =
 * This is a template to interact with OMERO. 
 * User can specify the ID of an "image","dataset","project","well","plate","screen"
 * The selected object is then imported in FIJI, with the specified resolution level
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
 * = DEPENDEsizeCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.19.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP 
 * 30.04.2025
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
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, IsizeCLUDING, 
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, IsizeCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (IsizeCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (IsizeCLUDING NEGLIGEsizeCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 */

/**
 * Main. Connect to OMERO, process images and disconnect from OMERO
 * 
 */

// Connection to server
host = "omero-server.epfl.ch"
port = 4064

Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())


if (user_client.isConnected()){
	println "Connected to "+host
	
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
		println "Importation in FIJI of "+object_type+", id "+id+", serie "+level+": DONE !"
		
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
	println "Retrieving pixel information for level "+level
	
	def pixels = image_wpr.getPixels()
	def sizeC = pixels.getSizeC()
	def sizeX0 = (int) (pixels.getSizeX())
	def sizeY0 = (int) (pixels.getSizeY())
	def sizeZ0 = (int) (pixels.getSizeZ())
	def sizeT = (int) (pixels.getSizeT())
    def spacingX  = pixels.getPixelSizeX();
    def spacingY  = pixels.getPixelSizeY();
    def spacingZ  = pixels.getPixelSizeZ();
    def stepT     = pixels.getTimeIncrement();
	
	// get the rawPixelStore and the resolution level
	RawPixelsStorePrx rawPixStore = user_client.getGateway().getPixelsStore(user_client.getCtx());
	rawPixStore.setPixelsId(pixels.getId(), false);
	int bytesWidth = rawPixStore.getByteWidth();
	int realLevel = rawPixStore.getResolutionLevels() - 1 - level;
	rawPixStore.setResolutionLevel(realLevel);
	
	// get the image size for this resolution level
	int minX = 0
	int minY = 0
	def sizeX = (int) (rawPixStore.getRowSize() / bytesWidth);
	def sizeY = (int) ( (rawPixStore.getPlaneSize() / sizeX) / bytesWidth );
	def sizeZ = (int) (pixels.getSizeZ())
	
	// get pixels information
	int pixelType = FormatTools.pixelTypeFromString(pixels.getPixelType());
	int bpp       = FormatTools.getBytesPerPixel(pixelType);
    boolean isFloat = FormatTools.isFloatingPoint(pixelType);
	    
	// create an imagePlus object
	ImagePlus imp = IJ.createHyperStack("test image", sizeX, sizeY, sizeC, sizeZ0, sizeT, bpp * 8);
	ImageStack stack = imp.getImageStack();
	
	// get the image at the specified resolution level
	for (int t = 0; t < sizeT; t++) {
		for (int z = 0; z < sizeZ0; z++) {
			for (int c = 0; c < sizeC; c++) {
				println "Reading image for resolution level "+level+ " ; c " + (c+1) + " ; z " + (z+1) + " ; t " + (t+1)
				def tile = rawPixStore.getTile(z, c, t, minX, minY, sizeX, sizeY);
				int n = imp.getStackIndex(c + 1, z + 1, t + 1);
				stack.setPixels(makeDataArray(tile, bpp, isFloat, false), n)
			}
		}
	}
	rawPixStore.close()
	imp.setStack(stack);
	
	// set the calibration
	def format = image_wpr.asDataObject().asImage().getFormat().getValue().getValue();
	def xResFactor = 1
	def yResFactor = 1
	def zResFactor = 1
	if (format.equals("CellSens")) {
		double downscalingFactor = Math.pow(2, level);
		xResFactor = downscalingFactor;
		yResFactor = downscalingFactor;
		zResFactor = 1;
	}
	else {
		xResFactor = (double) sizeX0 / (double) sizeX
		yResFactor = (double) sizeY0 / (double) sizeY
		zResFactor = (double) sizeZ0 / (double) sizeZ
	}

	def calibration = imp.getCalibration();
	image_wpr.setCalibration(pixels, calibration);
	calibration.pixelWidth = spacingX.getValue() * xResFactor
	calibration.pixelHeight = spacingY.getValue() * yResFactor
	if(spacingZ != null && zResFactor != null){
		calibration.pixelDepth = spacingZ.getValue() * zResFactor
	}
	if (stepT != null && !Double.isNaN(stepT.getValue())) {
        calibration.setTimeUnit(stepT.getSymbol());
        calibration.frameInterval = stepT.getValue();
    }
	imp.setCalibration(calibration);

	// show image
	if (showImages) imp.show()
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
import ij.*
import loci.formats.FormatTools;
import omero.api.RawPixelsStorePrx;
import static loci.common.DataTools.makeDataArray;