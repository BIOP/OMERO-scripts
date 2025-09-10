#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD
#@Long(label="Source dataset ID", value=119273) srcDatasetId
#@Long(label="Target dataset ID", value=119273) tgtDatasetId
#@File(label="CSV table") csvFile
#@Boolean(label="My file has a header",value=true) isHeader
#@String(label="PARENT DATASET ", visibility=MESSAGE, required=false) msg1
#@Boolean(label="Include transfer on source dataset ?",value=true) isDataset
#@String(label="TRANSFER CONFIGURATION", visibility=MESSAGE, required=false) msg2
#@Boolean(label="Tags",value=true) isTag
#@Boolean(label="Key-value pairs",value=true) isKVP
#@Boolean(label="ROIs",value=true) isROI
#@Boolean(label="Attachments",value=true) isAttachment
#@Boolean(label="Comments",value=true) isComment
#@Boolean(label="Ratings",value=true) isRating
#@Boolean(label="Description",value=true) isDescription
#@Boolean(label="Channels name",value=true) isChannels


/*
 * == DESCRIPTION ==  
 * This script aims at transferring all annotations from source images to target images. The source images must be
 * contained in one single dataset and the target images must be contained in another single dataset. Not all images
 * of the source dataset have to be processed and the target dataset may also contain images that are not mirrored in the
 * source dataset. Therefore, you need to fill a csv table with
 * 		- (optional) a header
 * 		- the name of each source image to copy annotations from in the FIRST column
 * 		- the name of each corresponding target image to transfer annotations to in the SECOND column
 * Running this script will ask you which annotations you want to transfer. You can also transfer the annotations linked to 
 * the source dataset by choosing the option.
 * At the end of processing, a CSV report is generated. This report is automatically saved in your "Download" folder.
 * The report contains a summary of the status of each annotations (Transferred, Skipped, Failed, or None) in the first part
 * and the content / size of what has been transferred in the second part.
 *  
 *  
 * == INPUTS ==
 *  - credentials 
 *  - Source dataset ID : Dataset where you want to copy the images annotations from
 *  - Source target ID : Dataset where you want to copy the images annotations to
 *  - CSV File : A csv-formatted file containing the list of source images (first column) and the list of target images
 *  			(second column). The file should be provided with a header (i.e. first line)
 *  - header : true if you have added a header to your csv file
 *  - Include parent dataset : True if you want to transfer annotation from the source dataset object to the target dataset
 *  - Transfer configuration : Chcek the box if you would like the annotation to be transferred. It automatically takes into
 *  							account if you checked or not "parent dataset transfer"
 * 
 * == OUTPUTS ==
 *  - Annotations transferring from source images to target images.
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.16.0 or later : https://github.com/GReD-Clermont/simple-omero-client
 * 
 * = INSTALLATION = 
 *  Open Script and Run
 * 
 * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV - PTECH - BIOP 
 * 2023.10.23
 * version v1.0.2
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
 * - Found a script here https://gist.github.com/will-moore/d8a12aa9124889440ff243c29c201a3e in Python but doesn't include everything
 * - 2024.05.10 : Update logger, CSV file generation and token separtor --v1.0.1
 * - 2025.09.10 : Save Fiji log window --v1.0.2
 */


/**
 * Main.
 * 
 */
 
// check the existence of a csv file
if(!csvFile.exists()){
	def message = "The file you provided '"+csvFile.getAbsolutePath() +"' doesn't exist"
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}
 
// Connection to server
host = "omero-server.epfl.ch"
port = 4064
Client user_client = new Client()

try{
	user_client.connect(host, port, USERNAME, PASSWORD.toCharArray())
}catch(Exception e){
	IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
	def message = "Cannot connect to "+host+". Please check your credentials"
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}

hasFailed = false
hasSilentlyFailed = false
message = ""
tokenSeparator = " | "
csvSeparator = ","

CTN = "Container"
SRC_IMG = "Source"
TGT_IMG = "Target"
SRC_IMG_ID = "Source ID"
TGT_IMG_ID = "Target ID"
TAG_ST = "Tag"
KVP_ST = "KVPs"
ROI_ST = "ROIs"
ATT_ST = "Attachments"
RTG_ST = "Rating"
CMT_ST = "Comments"
DPT_ST = "Description"
CHN_ST = "Channels name"
SKP = "Skipped by user"


if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	try{
		// load datasets
		def srcDatasetWpr
		def tgtDatasetWpr
		try{
			IJLoggerInfo("Loading", "Source Dataset "+srcDatasetId + " ...")
			srcDatasetWpr = user_client.getDataset(srcDatasetId)
		}catch(Exception e){
			hasFailed = true
			message = "The source dataset '"+srcDatasetId+"' doesn't exist on OMERO. Please check your dataset ID"
			IJLoggerError("Loading", message)
			throw e
		}
		try{
			IJLoggerInfo("Loading", "Target Dataset "+tgtDatasetId + " ...")
			tgtDatasetWpr = user_client.getDataset(tgtDatasetId)
		}catch(Exception e){
			hasFailed = true
			message = "The target dataset '"+tgtDatasetId+"' doesn't exist on OMERO. Please check your dataset ID"
			IJLoggerError("Loading", message)
			throw e
		}
		
		if(isDataset){
			Map<String, String> imgSummaryMap = new HashMap<>()
			imgSummaryMap.put(CTN,"Dataset")
			imgSummaryMap.put(SRC_IMG, srcDatasetWpr.getName())
			imgSummaryMap.put(TGT_IMG, tgtDatasetWpr.getName())
			imgSummaryMap.put(SRC_IMG_ID, String.valueOf(srcDatasetId))
			imgSummaryMap.put(TGT_IMG_ID, String.valueOf(tgtDatasetId))
			imgSummaryMap.putAll(doTransfer(user_client, srcDatasetWpr, tgtDatasetWpr, srcDatasetWpr.getName(), "dataset"))
			transferSummary.add(imgSummaryMap)
		}
		
		// Read CSV file
		IJLoggerInfo("Loading", "CSV file "+csvFile.getAbsolutePath() + " ...")
		def rows
		if(isHeader)
			rows = csvFile.readLines().findAll().tail()*.split(',')
		else
			rows = csvFile.readLines().findAll()*.split(',')
		
		for(List<String> names : rows){ 
			Map<String, String> imgSummaryMap = new HashMap<>()
			imgSummaryMap.put(CTN, "Image")
			
			// check if there is a target image
			if(names.size() > 1){
				def srcImgName = names[0]
				def tgtImgName = names[1]
				imgSummaryMap.put(SRC_IMG, srcImgName)
				imgSummaryMap.put(TGT_IMG, tgtImgName)
				
				// load source image
				def srcImgId
				try{
					IJLoggerInfo(srcImgName, "Loading source image "+srcImgName + " ...")
					srcImgId = getImgId(user_client, srcDatasetWpr, srcImgName)
					imgSummaryMap.put(SRC_IMG_ID, String.valueOf(srcImgId))
				}catch(Exception e){
					hasSilentlyFailed = true
					imgSummaryMap.put(SRC_IMG_ID, "Failed")
					message = "The source image '"+srcImgName+"' cannot be accessed"
					IJLoggerError(srcImgName, message, e)
					continue
				}
				
				// load the target image
				if(srcImgId > 0){
				
					def tgtImgId
					try{
						IJLoggerInfo(srcImgName, "Loading target image "+tgtImgName + " ...")
						tgtImgId = getImgId(user_client, tgtDatasetWpr, tgtImgName)
						imgSummaryMap.put(TGT_IMG_ID, String.valueOf(tgtImgId))
					}catch(Exception e){
						hasSilentlyFailed = true
						imgSummaryMap.put(TGT_IMG_ID, "Failed")
						message = "The target image '"+tgtImgName+"' cannot be accessed"
						IJLoggerError(srcImgName, message, e)
						continue
					}
					
					// do the transfer
					if(tgtImgId > 0){
						
						def srcImgWpr = user_client.getImage(srcImgId)
						def tgtImgWpr = user_client.getImage(tgtImgId)
						imgSummaryMap.putAll(doTransfer(user_client, srcImgWpr, tgtImgWpr, srcImgName, "image"))
						
						transferSummary.add(imgSummaryMap)
					}else{
						message = "The image '"+tgtImgName+"' doesn't exist in the target dataset "+tgtDatasetId
						hasSilentlyFailed = true
						IJLoggerError(srcImgName, message)
						transferSummary.add(imgSummaryMap)
					}
				}else{
					message = "The image '"+srcImgName+"' doesn't exist in the source dataset "+srcDatasetId
					hasSilentlyFailed = true
					IJLoggerError(srcImgName, message)
					transferSummary.add(imgSummaryMap)
				}
			}else{
				message = "The image '"+names[0]+"' doesn't have any target"
				hasSilentlyFailed = true
				IJLoggerError(names[0], message)
				imgSummaryMap.put(TGT_IMG, "No target")
				imgSummaryMap.put(SRC_IMG, names[0])
				transferSummary.add(imgSummaryMap)
			}
		}
		
		// final message
		if(hasSilentlyFailed){
			message = "The script ended with some errors."
		}
		else {
			message = "The annotations have been successfully transferred."
		}
		
		
	}catch(Exception e){
		IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
		if(!hasFailed){
			hasFailed = true
			message = "An error has occurred. Please look at the logs and the report to know where the processing has failed"
		}
	}finally{
		
		// generate CSV report
		try{
			IJLoggerInfo("CSV report", "Generate the CSV report...")
			generateCSVReport(transferSummary)
		}catch(Exception e2){
			IJLoggerError(e2.toString(), "\n"+getErrorStackTraceAsString(e2))
			hasFailed = true
			message = "An error has occurred during csv report generation."
		}finally{
			// disconnect
			user_client.disconnect()
			IJLoggerInfo("OMERO","Disconnected from "+host)
			
			// print final popup
			if(!hasFailed) {
				message += " A CSV report has been created in your 'Downloads' folder."
				if(hasSilentlyFailed){
					JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.WARNING_MESSAGE);
				}else{
					JOptionPane.showMessageDialog(null, message, "The end", JOptionPane.INFORMATION_MESSAGE);
				}
			}else{
				JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
}else{
	message = "Not able to connect to "+host
	IJLoggerError("OMERO", message)
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
}
return


/**
 * Transfer all annotations / rois / image details from the source image to the target image 
 * 
 */
def doTransfer(user_client, srcImgWpr, tgtImgWpr, srcImgName, container){
	Map<String, String> imgSummaryMap = new HashMap<>()
	
	if(isTag){
		// tag transfer
		try{
			IJLoggerInfo(srcImgName, "Transferring tags ...")
			def tags = transferTags(user_client, srcImgWpr, tgtImgWpr)
			imgSummaryMap.put(TAG_ST, tags.join(tokenSeparator))
			if(tags.isEmpty()){
				IJLoggerWarn(srcImgName, "No tags to transfer")
			}else{
				IJLoggerInfo(srcImgName, "DONE!")
			}
		}catch(Exception e){
			hasSilentlyFailed = true
			message = "An error occurred during tag transfer"
			IJLoggerError(srcImgName, message, e)
		}
	}else{
		imgSummaryMap.put(TAG_ST, SKP)
	}
	
	if(isKVP){
		// KVP transfer
		try{
			IJLoggerInfo(srcImgName, "Transferring Key Value pairs ...")
			def KVPs = transferKVPs(user_client, srcImgWpr, tgtImgWpr)
			imgSummaryMap.put(KVP_ST, KVPs.join(tokenSeparator))
			if(KVPs.isEmpty()){
				IJLoggerWarn(srcImgName, "No KVPs to transfer")
			}else{
				IJLoggerInfo(srcImgName, "DONE!")
			}
		}catch(Exception e){
			hasSilentlyFailed = true
			message = "An error occurred during key-values transfer"
			IJLoggerError(srcImgName, message, e)
		}
	}else{
		imgSummaryMap.put(KVP_ST, SKP)
	}
	
	if(isROI && container.toLowerCase().equals("image")){
		// ROIs transfer
		try{
			IJLoggerInfo(srcImgName, "Transferring ROIs ...")
			def ROIs = transferROIs(user_client, srcImgWpr, tgtImgWpr)
			imgSummaryMap.put(ROI_ST, ROIs.size() == 0 ? "" : String.valueOf(ROIs.size()))
			if(ROIs.isEmpty()){
				IJLoggerWarn(srcImgName, "No ROIs to transfer")
			}else{
				IJLoggerInfo(srcImgName, "DONE!")
			}
		}catch(Exception e){
			hasSilentlyFailed = true
			message = "An error occurred during ROIs transfer"
			IJLoggerError(srcImgName, message, e)
		}
	}else{
		imgSummaryMap.put(ROI_ST, SKP)
	}
	
	if(isAttachment){
		// attachments transfer
		try{
			IJLoggerInfo(srcImgName, "Transferring attachements ...")
			def attachments = transferAttachments(user_client, srcImgWpr, tgtImgWpr)
			imgSummaryMap.put(ATT_ST, attachments.join(tokenSeparator))
			if(attachments.isEmpty()){
				IJLoggerWarn(srcImgName, "No Attachments to transfer")
			}else{
				IJLoggerInfo(srcImgName, "DONE!")
			}
		}catch(Exception e){
			hasSilentlyFailed = true
			message = "An error occurred during attachments transfer"
			IJLoggerError(srcImgName, message, e)
		}
	}else{
		imgSummaryMap.put(ATT_ST, SKP)
	}
	
	if(isComment){
		// comments transfer
		try{
			IJLoggerInfo(srcImgName, "Transferring comments ...")
			def comments = transferComments(user_client, srcImgWpr, tgtImgWpr)
			imgSummaryMap.put(CMT_ST, comments.size() == 0 ? "" : String.valueOf(comments.size()))
			if(comments.isEmpty()){
				IJLoggerWarn(srcImgName, "No Comments to transfer")
			}else{
				IJLoggerInfo(srcImgName, "DONE!")
			}
		}catch(Exception e){
			hasSilentlyFailed = true
			message = "An error occurred during comment transfer"
			IJLoggerError(srcImgName, message, e)
		}
	}else{
		imgSummaryMap.put(CMT_ST, SKP)
	}
	
	if(isRating){
		// rating transfer
		try{
			IJLoggerInfo(srcImgName, "Transferring ratings ...")
			def rating = transferRatings(user_client, srcImgWpr, tgtImgWpr)
			imgSummaryMap.put(RTG_ST, rating == 0 ? "" : String.valueOf(rating))
			if(rating == 0){
				IJLoggerWarn(srcImgName, "No Rating to transfer")
			}else{
				IJLoggerInfo(srcImgName, "DONE!")
			}
		}catch(Exception e){
			hasSilentlyFailed = true
			message = "An error occurred during rating transfer"
			IJLoggerError(srcImgName, message, e)
		}
	}else{
		imgSummaryMap.put(RTG_ST, SKP)
	}
	
	if(isDescription){
		// description transfer
		try{
			IJLoggerInfo(srcImgName, "Transferring image description ...")
			def description = transferImgDescription(user_client, srcImgWpr, tgtImgWpr)
			imgSummaryMap.put(DPT_ST, description)
			if(description == null || description.isEmpty()){
				IJLoggerWarn(srcImgName, "No Description to transfer")
			}else{
				IJLoggerInfo(srcImgName, "DONE!")
			}
		}catch(Exception e){
			hasSilentlyFailed = true
			message = "An error occurred during description transfer"
			IJLoggerError(srcImgName, message, e)
		}
	}else{
		imgSummaryMap.put(DPT_ST, SKP)
	}
	
	if(isChannels && container.toLowerCase().equals("image")){
		// channels name transfer
		try{
			IJLoggerInfo(srcImgName, "Transferring channels name ...")
			def chNames = transferImgChannelName(user_client, srcImgWpr, tgtImgWpr)
			imgSummaryMap.put(CHN_ST, chNames.join(tokenSeparator))
			if(chNames.isEmpty()){
				IJLoggerWarn(srcImgName, "Images do not have the same the number of channels. No transfer !")
			}else{
				IJLoggerInfo(srcImgName, "DONE!")
			}
		}catch(Exception e){
			hasSilentlyFailed = true
			message = "An error occurred during channels name transfer"
			IJLoggerError(srcImgName, message, e)
		}
	}else{
		imgSummaryMap.put(CHN_ST, SKP)
	}
	
	return imgSummaryMap
}

/**
 * Create teh CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
	// define the header
	def commonHeaderList = [CTN, SRC_IMG, SRC_IMG_ID, TGT_IMG, TGT_IMG_ID]
	String commonHeader = commonHeaderList.join(csvSeparator)

	def statusHeaderList = [TAG_ST, KVP_ST, ROI_ST, ATT_ST, RTG_ST, CMT_ST, DPT_ST, CHN_ST]
	String statusHeader = statusHeaderList.join(csvSeparator)

	String statusOverallSummary = ""
	String contentOverallSummary = ""

	transferSummaryList.each{imgSummaryMap -> 
		def statusSummaryList = []
		commonHeaderList.each{outputParam->
			if(imgSummaryMap.containsKey(outputParam))
				statusSummaryList.add(imgSummaryMap.get(outputParam))
			else
				statusSummaryList.add("-")
		}
		
		def contentSummaryList = new ArrayList<>(statusSummaryList)
		statusHeaderList.each{outputParam->
			String tmpStatus = ""
			String tmpContent = ""
			(tmpStatus, tmpContent) = objectReport(imgSummaryMap, outputParam)
			statusSummaryList.add(tmpStatus)
			contentSummaryList.add(tmpContent)
		}
		
		statusOverallSummary += statusSummaryList.join(csvSeparator) + "\n"
		contentOverallSummary += contentSummaryList.join(csvSeparator) + "\n"
	}
	
	String content = commonHeader + csvSeparator + statusHeader + "\n" + statusOverallSummary + "\n" + contentOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour()+"_Transfer_Annotations_from_dataset_"+srcDatasetId+"_to_dataset_"+tgtDatasetId+"_report"
	String path = System.getProperty("user.home") + File.separator +"Downloads"
	IJLoggerInfo("CSV report", "Saving the report as '"+name+".csv' in "+path+"....")
	writeCSVFile(path, name, content)	
	IJLoggerInfo("CSV report", "DONE!")
		
	// save the log window
    saveFijiLogWindow(path, name)
}


/**
 * Basic summary method for one annotation
 */
def objectReport(imgSummaryMap, key){
	String statusSummary = ""
	String contentSummary = ""
	
	if(imgSummaryMap.containsKey(key)){
		def value = imgSummaryMap.get(key)
		if(value == null || value.isEmpty()){
			statusSummary += "-"
			contentSummary += "-"
		}
		else{
			if(value.equals(SKP)){
				statusSummary += "Skipped"
				contentSummary += "Skipped"
			}else{
				statusSummary += "Transferred"
				contentSummary += value
			}
		}
	} else {
		statusSummary += "Failed"
		contentSummary += "Failed"
	}
	
	return [statusSummary, contentSummary]
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
}


/**
 * Saves the Log of Fiji
 */
def saveFijiLogWindow(path, name){
	// create the path locally
    String filePath = path.toString() + File.separator + name + "_logs.txt";

	// select the log window
    IJ.selectWindow("Log")
    
    // save it
	IJ.saveAs("Text", filePath);
}


/**
 * return the Id of the image corresponding to the specified 'imgName^. If not found, return -1
 */
def getImgId(user_client, datasetWpr, imgName){
	def imgsList = datasetWpr.getImages(user_client, imgName)
	if(imgsList.isEmpty())
		return -1
		
	def imgWpr = imgsList.get(0)
	return imgWpr.getId()
}



/**
 * Transfer the linked tags from the source image to the target image.
 * Does not create a new tag ; just link the exisiting one to the target image
 * return the list of tags name linked to the source object.
 */
def transferTags(user_client, srcImgWpr, tgtImgWpr){
	def srcTagsList = srcImgWpr.getTags(user_client)
	def tgtTagsList = tgtImgWpr.getTags(user_client)
	
	// add all tags to the image
	tgtImgWpr.linkIfNotLinked(user_client, (TagAnnotationWrapper[])srcTagsList.toArray())	
	
	return srcTagsList.collect{it.getName()}
}


/**
 * Transfer the Key-Values pairs from the source image to the target image.
 * return the list of "Key:Value" linked to the source object.
 */
def transferKVPs(user_client, srcImgWpr, tgtImgWpr){
	// get the source key-value pairs
	List<List<NamedValue>> srcKeyValues = srcImgWpr.getMapAnnotations(user_client).stream()
																	   .map(MapAnnotationWrapper::getContent)
																	   .toList()
																	   
	srcKeyValues.each{keyValues ->				   
		MapAnnotationWrapper newKeyValues = new MapAnnotationWrapper(keyValues)
		newKeyValues.setNameSpace("openmicroscopy.org/omero/client/mapAnnotation")
		tgtImgWpr.linkIfNotLinked(user_client, newKeyValues)
	}
	
	return srcKeyValues.flatten().collect{it.name + ":"+it.value}
}



/**
 * Transfer the ROIs from the source image to the target image.
 * This REALLY transfers ROIs, meaning that the source image will not have ROIs anymore after the transfer.
 * ROIs have therefore the same ID.
 * return the list of ROIs linked to the source object.
 */
def transferROIs(user_client, srcImgWpr, tgtImgWpr){
	def srcROIsList = srcImgWpr.getROIs(user_client)
	tgtImgWpr.saveROIs(user_client, srcROIsList)
	
	return srcROIsList
}


/**
 * Transfer the linked attachments from the source image to the target image.
 * Does not create a new attachment ; just link the exisiting one to the target image
 * return the list of attachments name linked to the source object.
 */
def transferAttachments(user_client, srcImgWpr, tgtImgWpr){
	def srcFileList = srcImgWpr.getFileAnnotations(user_client)
	tgtImgWpr.linkIfNotLinked(user_client, (FileAnnotationWrapper[])srcFileList.toArray())
	
	return srcFileList.collect{it.getFileName()}
}

/**
 * Transfer the comments from the source image to the target image.
 * A new comment is created, with a new owner and a new date => this cannot be changed
 * return the list of the comments of the source object.
 */
def transferComments(user_client, srcImgWpr, tgtImgWpr){
	def srcComments = user_client.getMetadata().getAnnotations(user_client.getCtx(), srcImgWpr.asDataObject())
								.stream()
								.filter(comment-> comment instanceof TextualAnnotationData)
								.map(TextualAnnotationWrapper::new)
								.collect(Collectors.toList());
								
	tgtImgWpr.linkIfNotLinked(user_client, (TextualAnnotationWrapper[])srcComments.toArray())
	
	return srcComments
}


/**
 * Transfer the rating from the source image to the target image.
 * return the rating of the source object. If there is no rating, then the transfer if not done
 */
def transferRatings(user_client, srcImgWpr, tgtImgWpr){
	int rating = srcImgWpr.getMyRating(user_client)
	if(rating != 0)
		tgtImgWpr.rate(user_client, rating)
	
	return rating
}

/**
 * Transfer the image description from the source image to the target image.
 * return the description of the source object. If there is no description, then returns null
 */
def transferImgDescription(user_client, srcImgWpr, tgtImgWpr){
	def srcDescription = srcImgWpr.getDescription()
	tgtImgWpr.setDescription(srcDescription)
	
	tgtImgWpr.saveAndUpdate(user_client)
	
	return srcDescription
}


/**
 * Transfer the channels name from the source image to the target image.
 * return the list of channels name of the source object. If there is a mismatch in the number of channel, 
 * then returns an empty list
 */
def transferImgChannelName(user_client, srcImgWpr, tgtImgWpr){
	def srcChannels = srcImgWpr.getChannels(user_client)
	def tgtChannels = tgtImgWpr.getChannels(user_client)
	
	if(srcChannels.size() == tgtChannels.size()){
		def srcChannelNames = srcChannels.collect{it.getName()}
		[tgtChannels, srcChannelNames].transpose().each{tgtCh, srcName -> 
			tgtCh.setName(srcName)
		}
		
		user_client.getDm().updateObjects(user_client.getCtx(), tgtChannels
																		.stream()
																		.map(ChannelWrapper::asDataObject)
																		.map(ChannelData::asIObject)
																		.collect(Collectors.toList()), null)
		
		return srcChannelNames
	}else{
		println "The channels don't have the same size ; cannot transfer channel's name"
		return []
	}
}


/**
 * Logger methods
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}
def IJLoggerError(String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        "+message); 
}
def IJLoggerError(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [ERROR]        ["+title+"] -- "+message); 
}
def IJLoggerError(String title, String message, Exception e){
    IJLoggerError(title, message);
    IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e));
}
def IJLoggerError(String message, Exception e){
    IJLoggerError(message);
    IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e));
}
def IJLoggerWarn(String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    "+message); 
}
def IJLoggerWarn(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [WARNING]    ["+title+"] -- "+message); 
}
def IJLoggerInfo(String message){
	IJ.log(getCurrentDateAndHour() + "   [INFO]             "+message); 
}
def IJLoggerInfo(String title, String message){
	IJ.log(getCurrentDateAndHour() + "   [INFO]             ["+title+"] -- "+message); 
}
def getCurrentDateAndHour(){
    LocalDateTime localDateTime = LocalDateTime.now();
    LocalTime localTime = localDateTime.toLocalTime();
    LocalDate localDate = localDateTime.toLocalDate();
    return ""+localDate.getYear()+
            (localDate.getMonthValue() < 10 ? "0"+localDate.getMonthValue():localDate.getMonthValue()) +
            (localDate.getDayOfMonth() < 10 ? "0"+localDate.getDayOfMonth():localDate.getDayOfMonth())+"-"+
            (localTime.getHour() < 10 ? "0"+localTime.getHour():localTime.getHour())+"h"+
            (localTime.getMinute() < 10 ? "0"+localTime.getMinute():localTime.getMinute())+"m"+
            (localTime.getSecond() < 10 ? "0"+localTime.getSecond():localTime.getSecond());
}


/*
 * imports
 */
import fr.igred.omero.*
import fr.igred.omero.annotations.*
import fr.igred.omero.roi.*
import fr.igred.omero.repository.*
import javax.swing.JOptionPane; 
import omero.gateway.model.TextualAnnotationData;
import java.util.Collections;
import java.util.stream.Collectors;
import omero.model.NamedValue
import ij.IJ
import omero.gateway.model.ChannelData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;