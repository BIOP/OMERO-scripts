#@String(label="Username") USERNAME
#@String(label="Password", style='password' , value=PASSWORD , persist=false) PASSWORD
#@File(label="Folder",style="directory") rawFolder
#@String(choices={"New dataset", "Existing dataset"}, style="radioButtonHorizontal") choice
#@String(label="FOR AN EXISTING DATASET : ", visibility=MESSAGE, required=false) msg1
#@Long(label="Dataset ID", value=119273, required=false) datasetId
#@String(label="FOR A NEW DATASET : ", visibility=MESSAGE, required=false) msg2
#@Boolean(label="Use folder name",value=true,required=false) useFolderName
#@String(label="Dataset name if new dataset", value="name", required=false) newDatasetName
#@Long(label="Project ID if new dataset", value=119273, required=false) projectId
#@String(label="TAG CONFIGURATION : ", visibility=MESSAGE, required=false) msg3
#@Boolean(label="Image tags",value=true) imageTags
#@Boolean(label="Serie tags",value=false) serieTags
#@Boolean(label="Folder tags",value=false) folderTags

/* == CODE DESCRIPTION ==
 * This script batch imports images on OMERO and automatically add tags to the images.
 * All images, included those in child folders, will be uploaded IN THE SAME DATASET ON OMERO
 * The name of the image, as well as the name of the serie for fileset images, must be separated with underscores "_" to be parsable.
 * All commom images are supported EXCEPT : 
 * 		- .xml
 * 		- .xcde
 * 		- .ome
 * SCREENS ARE NOT SUPPORTED by this script
 * 
 * For ome.tiff images ONLY :
 * 		- group them by fileset and copy each filset in a separate folder
 * 		- the script will upload only the first image of the fileset ; all images within the fileset are automatically uploaded
 * 
 * The name of the image is parsed with:  underscores 
 * The serie name is pasred with:  underscores / space / comma.
 * Tokens are used as tags on OMERO.
 * If the name of the image is : 'tk1_tk2-tk3_tk4.tif [tk5_tk6, tk7_tk8]' then tags are : tk1 / tk2-tk3 / tk4 / tk5 / tk6 / tk7 / tk8
 * 
 * The folder hierarchy (with subfolders) is also added as tags on OMERO but THERE IS NO PARSING OF THE NAMES (no convention for folder name)
 * 
 * The script automatically generates a CSV report that summarizes which image has been uploaded, with which tags.
 * The CSV report is saved in your Downloads folder.
 * 		
 * == INPUTS ==
 *  - credentials 
 *  - folder to upload. 
 *  - You have the choice to create a new dataset on OMERO or to re-use an existing one
 * 		- If you choose to create a new dataset 
 * 			- enter the name of the new dataset OR use the name of the folder
 * 			- enter the ID of the project where to put the new dataset
 * 		- If you choose to upload projections in an existing
 * 			- enter the ID of the dataset
 * 	- You can configure the tags you want to add to your images on OMERO
 * 		- Image tags : onyl image name (without serie name) is parsed (with underscore parsing)
 * 		- Folder hierarchy : The folder and sub-folders name are used as tags (with underscore parsing)
 * 		- Serie name : the name of the serie is parsed (with underscore, coma and space parsing)
 * 	
 * 
 * == OUTPUTS ==	
 *  - image on OMERO
 *  - Tags on OMERO
 *  - CSV report in the Download folder
 * 
 * 
 * = DEPENDENCIES =
 *  - Fiji update site OMERO 5.5-5.6
 *  - simple-omero-client-5.15.0 https://github.com/GReD-Clermont/simple-omero-client
 *  
 *  
 *  = AUTHOR INFORMATION =
 * Code written by Rémy Dornier - EPFL - SV - PTECH - BIOP
 * date : 2023-08-10
 * version : v2.1
 * 
 * = HISTORY =
 * - 2023.08.10 : First release --v1.0
 * - 2023.10.04 : Fix bug on GUI + remove numeric tags (i.e. only numbers) --v1.1
 * - 2023.11.07 : Add support for ndpi files --v2.0
 * - 2023.11.07 : Add popup messages --v2.0
 * - 2023.11.07 : Catch errors --v2.0
 * - 2023.11.07 : Generate a CSV report in the download folder --v2.0
 * - 2024.02.29 : Add support for the fluorescence VSI images from new Slide Scanner (i.e. split serie name with comma) --v2.1
 * 
 */

// check the validity of user parameters
if(choice == "Existing dataset" && (datasetId == null || datasetId < 0)){
	def message = "The dataset ID is not correct"
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}

if(choice == "New dataset" && (projectId == null || projectId < 0)){
	def message = "The project ID is not correct"
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}

if(choice == "New dataset" && !useFolderName && (newDatasetName == null || newDatasetName.isEmpty())){
	def message = "The dataset name is not correct"
	JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
	return
}


// global constants
SCREEN = "screen"
STANDARD = "standard"
OME_TIFF = "ome_tiff"
def compatibleFormatList =  [".tif", ".tiff", ".ome.tif", ".ome.tiff", ".lif", ".czi", ".nd", ".nd2", ".vsi", ".lsm", ".dv", ".zvi",
	".png", ".jpeg", ".stk", ".gif", ".jp2", ".xml", ".jpg", ".pgm", ".qptiff", ".h5", ".avi", ".ics", ".ids", ".lof", ".oif", ".tf2",
	".tf8", ".btf", ".pic", ".ims", ".bmp", ".xcde", ".ome", ".svs", ".ndpi", ".dcm", ".oir"]

hasFailed = false
hasSilentlyFailed = false
message = ""

PAR_FOL = "Parent folder"
IMG_NAME = "Image name"
IMG_PATH = "Local Path"
OMR_ID = "OMERO ID"
TYPE = "Type"
STS = "Status"
CMP = "OMERO Compatibility"
TAG = "Tags"

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

if (user_client.isConnected()){
	IJLoggerInfo("OMERO","Connected to "+host)
	List<Map<String, String>> transferSummary = new ArrayList<>()
	
	try{		
		// get project in OMERO
		def projectWrapper
		try{
    		IJLoggerInfo("OMERO","Getting project "+projectId)
    		projectWrapper = user_client.getProject(projectId) 
		}catch(Exception e){
			hasFailed = true
			message = "The project '"+projectId+"' cannot be found on OMERO."
			IJLoggerError("OMERO", message)
			throw e
		}
		
		// get the dataset or create it
		def datasetWrapper
		if(choice == "New dataset"){
			try{
				def dstName = useFolderName ? rawFolder.getName() : newDatasetName
    			IJLoggerInfo("OMERO","Creating a new dataset " + dstName);
    			datasetWrapper = createOmeroDataset(user_client, projectWrapper, dstName)
			}catch (Exception e){
				hasFailed = true
    			message = "The dataset '"+dstName+"' cannot be created on OMERO."
				IJLoggerError("OMERO", message)
				throw e
			}
		}
		else{
			try{
    			IJLoggerInfo("OMERO","Getting dataset "+datasetId)
				datasetWrapper = user_client.getDataset(datasetId) 
			}catch(Exception e){
				hasFailed = true
    			message = "The dataset '"+datasetId+"' cannot be found on OMERO."
				IJLoggerError("OMERO", message)
				throw e
			}
		}
		
		IJLoggerInfo("OMERO","Images will be imported in project '"+projectWrapper.getName()+ "' ; dataset '"+datasetWrapper.getName()+"'.");
		IJLoggerInfo("","*****************");
		
		// get images to process
		IJLoggerInfo("Reading", "List all images from '" + rawFolder.getName() + "'");
		def uList;
		def cMap;
		def imgMap
		try{
			(cMap, imgMap, uList) = listImages(rawFolder, compatibleFormatList)
		}catch(Exception e){
		    hasFailed = true
			message = "An error occurred when listing images from '"+rawFolder.getAbsolutePath()+"'."
			IJLoggerError("Folder '"+rawFolder.getName()+"'", message)
			throw e
		}
		
		def option = JOptionPane.OK_OPTION

		// check if there are ome-tiff images. In case, inform the user
		def values = cMap.values()
		if(values.contains(OME_TIFF)){
			def title = "OME_TIFF Files"
			def content = "There are some .ome.tiff files. Please, check that all and only files from the same fileset are in the same folder. "+
						"If it is not the case, CANCEL the script, separate them in different folders and restart the script"
			option = JOptionPane.showConfirmDialog(new JFrame(), content, title, JOptionPane.OK_CANCEL_OPTION);		
		}
		
		// upload images
		if(option == JOptionPane.OK_OPTION){
			def countRaw = 0
			for(File parentFolder : cMap.keySet()){
				def type = cMap.get(parentFolder)
				if(type == STANDARD){
					// upload all images
					for(File imgFile : imgMap.get(parentFolder)){
						IJLoggerInfo("", "*****************");
						Map<String, String> imgSummaryMap = new HashMap<>()
						imgSummaryMap.put(PAR_FOL, rawFolder.toPath().relativize(parentFolder.toPath()).toString())
						imgSummaryMap.put(IMG_NAME, imgFile.getName())
						imgSummaryMap.put(IMG_PATH, imgFile.getAbsolutePath())
						imgSummaryMap.put(TYPE, type)
						imgSummaryMap.put(CMP, "Compatible")
				
						// upload image
						def ids = []
						try{
							IJLoggerInfo(imgFile.getName(),"Upload on OMERO...")
							ids = saveImageOnOmero(user_client, imgFile, datasetWrapper)
							countRaw += ids.size()
							imgSummaryMap.put(OMR_ID, ids.join(" ; "))
							imgSummaryMap.put(STS, "Uploaded")
						}catch(Exception e){
							hasSilentlyFailed = true
			    			message = "Impossible to upload this image on OMERO"						
							IJLoggerError(imgFile.getName(), message)
							IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
							continue
						}
						
						// link tags
						try{
							IJLoggerInfo(imgFile.getName(),"Parse image name and link tags to OMERO...")
							def tags = linkTagsToImage(user_client, ids, imgFile, rawFolder)
							imgSummaryMap.put(TAG, tags.join(" ; "))
						}catch(Exception e){
							hasSilentlyFailed = true
			    			message = "Impossible to link tags to this image"
							IJLoggerError(imgFile.getName(), message)
							IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
							continue
						}
						transferSummary.add(imgSummaryMap)
					}
				}else if(type == OME_TIFF){
					// upload only the first image as it is part of a fileset and the entire fileset will be uploaded
					def child = imgMap.get(parentFolder)
					countRaw += child.length
					
					Map<String, String> imgSummaryMap = new HashMap<>()
					imgSummaryMap.put(PAR_FOL, rawFolder.toPath().relativize(parentFolder.toPath()).toString())
					imgSummaryMap.put(IMG_NAME, child.get(0).getName())
					imgSummaryMap.put(IMG_PATH, child.get(0).getAbsolutePath())
					imgSummaryMap.put(TYPE, type)
					imgSummaryMap.put(CMP, "Compatible")

					// upload image
					def ids = []
					try{
						IJLoggerInfo(child.get(0).getName(),"Upload on OMERO...")
						ids = saveImageOnOmero(user_client, child.get(0), datasetWrapper)
						countRaw += ids.size()
						imgSummaryMap.put(OMR_ID, ids.join(" ; "))
						imgSummaryMap.put(STS, "Uploaded")
					}catch(Exception e){
						hasSilentlyFailed = true
		    			message = "Impossible to upload this image on OMERO"
						IJLoggerError(child.get(0).getName(), message)
						IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
						continue
					}
					
					// link tags
					try{
						IJLoggerInfo(child.get(0).getName(),"Parse image name and link tags to OMERO...")
						def tags = linkTagsToImage(user_client, ids, child.get(0), rawFolder)
						imgSummaryMap.put(TAG, tags.join(" ; "))
					}catch(Exception e){
						hasSilentlyFailed = true
		    			message = "Impossible to link tags to this image"
						IJLoggerError(child.get(0).getName(), message)
						IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
						continue
					}
					transferSummary.add(imgSummaryMap)
				}else{
					IJLoggerWarn("Supported Format","Screens are not supported : "+parentFolder.getAbsolutePath())
					Map<String, String> imgSummaryMap = new HashMap<>()
					imgSummaryMap.put(TYPE, type)
					imgSummaryMap.put(PAR_FOL, rawFolder.toPath().relativize(parentFolder.toPath()).toString())
					imgSummaryMap.put(IMG_NAME, parentFolder.getName())
					imgSummaryMap.put(IMG_PATH, parentFolder.getAbsolutePath())
					imgSummaryMap.put(CMP, "Not Supported")
					transferSummary.add(imgSummaryMap)
				}
				IJLoggerInfo("", "*****************");
			}
			
			IJLoggerInfo("OMERO", countRaw + " files uploaded on OMERO");
			
			uList.each{
				Map<String, String> imgSummaryMap = new HashMap<>()
				imgSummaryMap.put(PAR_FOL, rawFolder.toPath().relativize(it.getParentFile().toPath()).toString())
				imgSummaryMap.put(IMG_NAME, it.getName())
				imgSummaryMap.put(IMG_PATH, it.getAbsolutePath())
				imgSummaryMap.put(CMP, "Uncompatible")
				transferSummary.add(imgSummaryMap)
			}
			
			if(hasSilentlyFailed)
				message = "The script ended with some errors."
			else 
				message = "The upload and tagging have been successfully done."
		}else{
			message = "The script was ended by the user."
		}
	}catch(Exception e){
		IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
		if(!hasFailed){
			hasFailed = true
			message = "An error has occurred. Please look at the logs and the report to know where the processing has failed."
		}
	}finally{
		// generate CSV report
		try{
			IJLoggerInfo("CSV report", "Generate the CSV report...")
			generateCSVReport(transferSummary)
		}catch(Exception e2){
			IJLoggerError(e2.toString(), "\n"+getErrorStackTraceAsString(e2))
			hasFailed = true
			message += " An error has occurred during csv report generation."
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
 * List compatible images to upload within parentFolder
 * 
 */
def listImages(parentFolder, compatibleFormatList){
	def parentFolderMap = new HashMap<>()
	def fileImgMap = new HashMap<>()
	def uncompatibleList = []
	def isScreeningImage = false

	parentFolder.listFiles().each{imgFile->
		// check for file
		if(imgFile.isFile()){
			// remove all hidden files
			if(!imgFile.isHidden()){
				def imgName = imgFile.getName()
				// remove all images belonging to HCS
				if(!isScreeningImage){
					// check file compatibility
					if(isCompatibleImage(imgName, compatibleFormatList)){
						def parent = imgFile.getParentFile()
						// filter HCS file format
						if(imgName.endsWith(".xml") || imgName.endsWith(".xdce") || imgName.endsWith(".ome")){
							IJLoggerWarn("Supported Format", "Screening images are not supported")
							isScreeningImage = true
							parentFolderMap.put(parent, SCREEN)
						}else{
							// special treatement for ome-tiff files
							if(imgName.endsWith(".ome.tif") || imgName.endsWith(".ome.tiff")){
								IJLoggerWarn("Supported Format", "There are some ome.tiff files. Only one file per folder will be uploaded on OMERO.")
								parentFolderMap.put(parent, OME_TIFF)
							}else{
								parentFolderMap.put(parent, STANDARD)
							}
							// add the file to the list
							def files
							if(fileImgMap.containsKey(parent)){
								files = fileImgMap.get(parent)
							}else{
								files = new ArrayList<>()
							}
							files.add(imgFile)
							fileImgMap.put(parent, files)
						}
					}else{
						uncompatibleList.add(imgFile)
						IJLoggerWarn("Supported Format", "Uncompatible file "+imgFile.getAbsolutePath())
					}
				}
			}
		
		// if the file if a directory, recursie call to the method to list child files
		}else if(imgFile.isDirectory() && !imgFile.getName().startsWith("_")){
			def imgMap;
			def cMap;
			def uList
			(cMap, imgMap, uList) = listImages(imgFile, compatibleFormatList)
			parentFolderMap.putAll(cMap)
			fileImgMap.putAll(imgMap)
			uncompatibleList.addAll(uList)
		}
	}
	
	return [parentFolderMap, fileImgMap, uncompatibleList]
}



/**
 * Check the file compatibility i.e. should only be bio-formats compatible images
 */
def isCompatibleImage(name, compatibleFormatList){
	def lowerCaseName = name.toLowerCase()
	for(String format : compatibleFormatList){
		if(lowerCaseName.endsWith(format))
			return true
	}
	return false
}


/**
 * return the dataset wrapper corresonding a new dataset or to an existing dataset, depending on the user choice
 */
def createOmeroDataset(user_client, projectWrapper, datasetName){	
	//create a new dataset 
    def dataset = new DatasetData();
	dataset.setName(datasetName);
    
    // send the dataset on OMERO
    dataset = user_client.getDm().createDataset(user_client.getCtx(), dataset, projectWrapper.asProjectData())
    def newId = dataset.getId()
    
    // get the corresponding wrapper
    def datasetWrapper = user_client.getDataset(newId)
	
	return datasetWrapper
}


/**
 * Upload the image on OMERO, in the specified dataset
 * 
 */
def saveImageOnOmero(user_client, imgFile, datasetWrapper){
    def imageIds = datasetWrapper.importImage(user_client, imgFile.getAbsolutePath())
    return imageIds
}


/**
 * Add a list of tags to the specified image
 * 
 */
def saveTagsOnOmero(tags, imgWpr, user_client){
	def tagsToAdd = []
	
	// get existing tags
	def groupTags = user_client.getTags()
	def imageTags = imgWpr.getTags(user_client)
	
	// find if the tag to add already exists on OMERO. If yes, they are not added twice
	tags.each{tag->
		if(tagsToAdd.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } == null){
			// find if the requested tag already exists
			new_tag = groupTags.find{ it.getName().toLowerCase().equals(tag.toLowerCase()) } ?: new TagAnnotationWrapper(new TagAnnotationData(tag))
			
			// add the tag if it is not already the case
			imageTags.find{ it.getName().toLowerCase().equals(new_tag.getName().toLowerCase()) } ?: tagsToAdd.add(new_tag)
		}
		
	}
	
	// add all tags to the image
	imgWpr.addTags(user_client, (TagAnnotationWrapper[])tagsToAdd.toArray())
}


/**
 * upload images, parse the image name and add tags on OMERO
 */
def linkTagsToImage(user_client, ids, imgFile, rawFolder){
	def nameToParse = imgFile.getName().split("\\.")[0]
	def uploadedTags = []
	def patternSerieName = /[_, ]/

	if(serieTags || folderTags || imageTags){
		ids.each{id->
			String log = ""
			log += "Create tags from : "
			def imgWrapper = user_client.getImage(id)
			def omeImgName = imgWrapper.getName()
			def tags = []
			
			if(imageTags){
				log += "image name, "
				tags = nameToParse.split("_") as List
			}
			
			if(serieTags){
				log += "serie name, "
				if(!omeImgName.equals(imgFile.getName())){
					// define the regex
					def pattern = /.*\[(?<serie>.*)\]/
					def regex = Pattern.compile(pattern)
					def matcher = regex.matcher(omeImgName)
					
					if(matcher.find()){
						def serieName = matcher.group("serie")
						tags.addAll(serieName.split(patternSerieName) as List)
					}
				}
			}
			
			if(folderTags){
				log += "folder name, "
				def parentName = rawFolder.getName()
				tags.addAll(parentName.split("_") as List)
				def childHierarchyPath = imgFile.getAbsolutePath().replace(rawFolder.getAbsolutePath(),"").replace(imgFile.getName(),"")
				def folders = childHierarchyPath.split("\\"+File.separator)
				folders.each{folder ->
					if(!folder.isEmpty()){
						tags.addAll(folder.split("_") as List)
					}
				}
			}
			
			// remove tags that are only numbers
			def filteredTags = []
			tags.each{tag->
				try{
					Integer.parseInt(tag)
				}catch(Exception e){
					filteredTags.add(tag)
				}
			}
			
			log += " :  " + filteredTags.join(" ; ")
			saveTagsOnOmero(filteredTags.findAll(), imgWrapper, user_client)
			log += "...... Done !"
			IJLoggerInfo(imgWrapper.getName(), log)
			
			uploadedTags.addAll(filteredTags)
		}
	}
	
	return uploadedTags.unique()
}



/**
 * Create teh CSV report from all info cleecting during the processing
 */
def generateCSVReport(transferSummaryList){
	// define the header
	String header = PAR_FOL + "," + IMG_NAME + "," + IMG_PATH + "," + CMP + "," + STS + "," + TYPE + "," + OMR_ID + "," + TAG

	String statusOverallSummary = ""

	transferSummaryList.each{imgSummaryMap -> 
		String statusSummary = ""
		
		// Source image
		statusSummary += imgSummaryMap.get(PAR_FOL)+","
		statusSummary += imgSummaryMap.get(IMG_NAME)+","
		statusSummary += imgSummaryMap.get(IMG_PATH)+","
		statusSummary += imgSummaryMap.get(CMP)+","
		
		// Source image id
		if(imgSummaryMap.containsKey(STS))
			statusSummary += imgSummaryMap.get(STS)+","
		else
			statusSummary += "Failed,"

		// Target image
		if(imgSummaryMap.containsKey(TYPE))
			statusSummary += imgSummaryMap.get(TYPE)+","
		else
			statusSummary += " - ,"

		// Target image id
		if(imgSummaryMap.containsKey(OMR_ID))
			statusSummary += imgSummaryMap.get(OMR_ID)+","
		else
			statusSummary += " - ,"
			
		// tags
		if(imgSummaryMap.containsKey(TAG))
			statusSummary += imgSummaryMap.get(TAG)+","
		else
			statusSummary += " - ,"
		
		statusOverallSummary += statusSummary + "\n"
	}
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour()+"_Upload_from_"+rawFolder.getName()+"_to_OMERO_report"
	String path = System.getProperty("user.home") + File.separator +"Downloads"
	IJLoggerInfo("CSV report", "Saving the report as '"+name+".csv' in "+path+"....")
	writeCSVFile(path, name, content)	
	IJLoggerInfo("CSV report", "DONE!")
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
 * Logger methods
 */
def getErrorStackTraceAsString(Exception e){
    return Arrays.stream(e.getStackTrace()).map(StackTraceElement::toString).reduce("",(a, b)->a + "     at "+b+"\n");
}
def IJLoggerError(String title, String message){
	IJ.log("[ERROR]   ["+title+"] -- "+message); 
}
def IJLoggerWarn(String title, String message){
	IJ.log("[WARNING]   ["+title+"] -- "+message); 
}
def IJLoggerInfo(String title, String message){
	IJ.log("[INFO]   ["+title+"] -- "+message); 
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
import javax.swing.JOptionPane;
import javax.swing.JFrame;
import fr.igred.omero.*
import fr.igred.omero.repository.*
import fr.igred.omero.annotations.*
import omero.gateway.model.DatasetData
import omero.gateway.model.ProjectData
import omero.gateway.model.TagAnnotationData;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane; 
import java.nio.charset.StandardCharsets;
import ij.IJ;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;