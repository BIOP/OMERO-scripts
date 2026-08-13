#@String(label="Host", value="omero-server.epfl.ch") host
#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Image ID", value=119273) id

/*  
 * Create a TextROI and attached it to the given image on OMERO
 * 
 *  
 * Dependencies
 *  - Fiji update site OMERO 5.5-5.6
 *  - Fiji update site PTBIOP, with simple-omero-client
 * 
 * Author: Rémy Dornier, EPFL - PTBIOP 
 * Date: 2025.11.06
 * Version: 1.0.1
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
 * - 2026.08.13 : Automatically switch group if the object is not coming from the default one -v1.0.1 
 */


// Connection to server
port = 4064
Client user_client = new Client()
user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())

if (user_client.isConnected()){
	println "Connected to "+host

	try{
		checkAndSwitchGroup(user_client, "ImageData", id)
		processImage(user_client, user_client.getImage(id))
		println "Processing of image, id "+id+": DONE !"
	} finally {
		user_client.disconnect()
		println "Disconnected from "+host
	}
} else {
	println "Not able to connect to "+host
}
return



def checkAndSwitchGroup(user_client, dataType, dataId){
    // get the group ID and switch context to that group
    def img = user_client.getBrowseFacility().findObject(user_client.getCtx(), dataType, dataId, true);
    def groupId = img.getGroupId();

    if(groupId > 0) {
        if (user_client.getCurrentGroupId() != groupId){
        	println "Switching group from "+user_client.getGroup(user_client.getCurrentGroupId()).getName()+" to "+user_client.getGroup(groupId).getName()
            user_client.switchGroup(groupId);
        }
    }
    return groupId
}

def processImage(user_client, image_wpr){
	println image_wpr.getName()

  	def txtroi1 = new TextRoi( 0 , 0 ,50.0 , 50.0,"myTextArial" ,  new FontUtil().getFont("Arial", 2 , 72 as float ) )
	
	def roiArray = [txtroi1]
	def roisToUpload = ROIWrapper.fromImageJ(roiArray)
	/*// define a new Text ROI (sinple-omero-client ROI)
	def text = new TextWrapper(new TextData("myText", 0, 0))
	// get the shape settings
	def settings = text.asDataObject().getShapeSettings()
	// set the font size
	settings.setFontSize(new LengthI(72, UnitsLength.MILLIMETER))
	// create a list of ROI to upload
	def roisToUpload = []
	roisToUpload.add(text)
	
	// convert to a list of ROIWrapper
	def roisToUpload2 = []
	roisToUpload2.add(new ROIWrapper(roisToUpload))
*/

	// upload to OMERO
	image_wpr.saveROIs(user_client, roisToUpload)
}



/*
 * imports  
 */
import fr.igred.omero.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import omero.gateway.model.*
import omero.model.*
import omero.model.enums.*
import ij.gui.TextRoi
import ij.util.FontUtil
