#@String(label="Username") USERNAME
#@String(label="Password", style='password', persist=false) PASSWORD


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
 * version : v2.2.0
 * 
 * = HISTORY =
 * - 2023.08.10 : First release --v1.0
 * - 2023.10.04 : Fix bug on GUI + remove numeric tags (i.e. only numbers) --v1.1
 * - 2023.11.07 : Add support for ndpi files --v2.0
 * - 2023.11.07 : Add popup messages --v2.0
 * - 2023.11.07 : Catch errors --v2.0
 * - 2023.11.07 : Generate a CSV report in the download folder --v2.0
 * - 2024.02.29 : Add support for the fluorescence VSI images from new Slide Scanner (i.e. split serie name with comma) --v2.1
 * - 2024.05.10 : Update logger, CSV file generation and token separtor --v2.1.1
 * - 2025.03.14 : Handle standalone ome.tiff files --v2.2.0
 * 
 */


// global constants
SCREEN = "screen"
STANDARD = "standard"
OME_TIFF = "ome_tiff"
def compatibleFormatList =  [".tif", ".tiff", ".ome.tif", ".ome.tiff", ".lif", ".czi", ".nd", ".nd2", ".vsi", ".lsm", ".dv", ".zvi",
	".png", ".jpeg", ".stk", ".gif", ".jp2", ".xml", ".jpg", ".pgm", ".qptiff", ".h5", ".avi", ".ics", ".ids", ".lof", ".oif", ".tf2",
	".tf8", ".btf", ".pic", ".ims", ".bmp", ".xcde", ".ome", ".svs", ".ndpi", ".dcm", ".oir"]

hasFailed = false
hasSilentlyFailed = false
endedByUser = false
omeTiffFilesWarning = false
message = ""
tokenSeparator = " | "
csvSeparator = ","

RAW_FOL = "Raw folder"
PRJ_ID = "Project ID"
DST_ID = "Dataset ID"
PRJ = "Project name"
DST = "Dataset name"
IMG_NAME = "Image name"
IMG_PATH = "Local Path"
OMR_ID = "OMERO ID"
TYPE = "Type"
STS = "Status"
CMP = "OMERO Compatibility"
TAG = "Tags"


String DEFAULT_PATH_KEY = "scriptDefaultDir"
String FOL_PATH = "path";
String IS_OME_TIFF = "isOmeTiff"
String OME_TIFF_STD = "isOmeTiffStandalone"
String OMR_PRJ = "project";
String OMR_DST = "dataset";
String IS_NEW_DST = "isNewDataset";
String IS_DST_FLD = "isNewFromFolder";
String IS_NEW_PRJ = "isNewProject";
String DST_NAME = "datasetName";
String PRJ_NAME = "projectName";
String TAG_IMG = "tagImage";
String TAG_SER = "tagSerie";
String TAG_FOL = "tagFolder";

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
	Map<String, String> commonSummaryMap = new HashMap<>()
	def countImg = 0;
	
	try{		
		
		// get the userID
		def userId = user_client.getId()
		
		// get user's projects
		def projectWrapperList = user_client.getProjects(user_client.getUser())
		
		// get project's name
		def projectNames = (String[])projectWrapperList.stream().map(ProjectWrapper::getName).collect(Collectors.toList()).sort()
		
		// generate the dialog box
		def dialog = new Dialog(user_client, projectNames, projectWrapperList, userId)
	
		while(!dialog.getEnterPressed()){
	   		// Wait an answer from the user (Ok or Cancel)
		}
		
		// If Ok
		if(dialog.getValidated()){		
			for(Map<String, String> selectedMap : dialog.getSelectedList()){
				
				// collect the user inputs
				String inputValues = selectedMap.collect{key, value -> ""+key+":"+value}.join("\n")
				IJLoggerInfo("LOCAL", "User input\n " + inputValues);
			
				String foldersToImport = selectedMap.get(FOL_PATH)
				boolean isOmeTiff = selectedMap.get(IS_OME_TIFF).toLowerCase().equals("true") ? true: false
				boolean isOmeTiffStandalone = selectedMap.get(OME_TIFF_STD).toLowerCase().equals("true") ? true: false
				boolean isNewDataset = selectedMap.get(IS_NEW_DST).toLowerCase().equals("true") ? true: false
				boolean isNewFromFolder = selectedMap.get(IS_DST_FLD).toLowerCase().equals("true") ? true: false
				boolean isNewProject = selectedMap.get(IS_NEW_PRJ).toLowerCase().equals("true") ? true: false
				String existingProjectName = selectedMap.get(OMR_PRJ)
				String existingDatasetName = selectedMap.get(OMR_DST)
				String newProjectName = selectedMap.get(PRJ_NAME)
				String newDatasetName = selectedMap.get(DST_NAME)
				boolean imageTags = selectedMap.get(TAG_IMG).toLowerCase().equals("true") ? true: false
				boolean serieTags = selectedMap.get(TAG_SER).toLowerCase().equals("true") ? true: false
				boolean folderTags = selectedMap.get(TAG_FOL).toLowerCase().equals("true") ? true: false
		
				// getting the project
				def projectWrapper
				if(!isNewProject){
					IJLoggerInfo("OMERO", "Getting the project '" + existingProjectName +"'");
					projectWrapper = projectWrapperList.find{it.getName() == existingProjectName}
				}else{
					try{
						IJLoggerInfo("OMERO", "Creating a new project '" + newProjectName +"'");
						projectWrapper = createOmeroProject(user_client, newProjectName)
						projectWrapperList.add(projectWrapper)
					}catch (Exception e){
						hasSilentlyFailed = true
						message = "The project '"+newProjectName+"' cannot be created on OMERO."
						IJLoggerError("OMERO", message, e)
						foldersToImport.split(",").each{
							Map<String, String> imgSummaryMap = new HashMap<>()
							File folder = new File(it)
							imgSummaryMap.put(PAR_FOL, folder.getName())
							imgSummaryMap.putAll(commonSummaryMap)
							transferSummary.add(imgSummaryMap)
						}
						continue
					}
				}
				
				commonSummaryMap.put(PRJ, projectWrapper.getName())
				commonSummaryMap.put(PRJ_ID, ""+projectWrapper.getId())
				
				Map<String, DatasetWrapper> datasetsList = new HashMap<>()
				def datasetWrapper
				
				for(String folderPath : foldersToImport.split(",")){
					Map<String, String> datasetSummaryMap = new HashMap<>()
					File rawFolder = new File(folderPath)
					
					commonSummaryMap.put(RAW_FOL, rawFolder.getName())
					
					// getting the dataset
					if(isNewFromFolder){
						isNewDataset = true
						datasetWrapper = null
						newDatasetName = rawFolder.getName()
					}
					
					// getting the dataset
					if(datasetWrapper == null){
						if(!isNewDataset){
							try{
								IJLoggerInfo("OMERO", "Getting the dataset '" + existingDatasetName +"'");
								if(datasetsList.containsKey(existingDatasetName))
									datasetWrapper = datasetsList.get(existingDatasetName)
								else{
									datasetWrapper = projectWrapper.getDatasets(existingDatasetName).get(0)
									datasetsList.put(existingDatasetName, datasetWrapper)
								}
							}catch (Exception e){
								hasSilentlyFailed = true
								message = "The dataset '"+existingDatasetName+"' cannot be retrieved from OMERO."
								IJLoggerError("OMERO", message, e)
								Map<String, String> imgSummaryMap = new HashMap<>()
								imgSummaryMap.putAll(commonSummaryMap)
								transferSummary.add(imgSummaryMap)
								continue
							}
						} else {
							try{				
								IJLoggerInfo("OMERO", "Creating a new dataset '" + newDatasetName +"'");
								datasetWrapper = createOmeroDataset(user_client, projectWrapper, newDatasetName)
								datasetsList.put(newDatasetName, datasetWrapper)
							}catch (Exception e){
								hasSilentlyFailed = true
								message = "The dataset '"+newDatasetName+"' cannot be created on OMERO."
								IJLoggerError("OMERO", message, e)
								Map<String, String> imgSummaryMap = new HashMap<>()
								imgSummaryMap.putAll(commonSummaryMap)
								transferSummary.add(imgSummaryMap)
								continue
							}
						}
					}
					
					commonSummaryMap.put(DST, datasetWrapper.getName())
					commonSummaryMap.put(DST_ID, ""+datasetWrapper.getId())
			
					IJLoggerInfo("OMERO","Images will be imported in project '"+projectWrapper.getName()+ "' ; dataset '"+datasetWrapper.getName()+"'.");
					IJLoggerInfo("*****************");
					
					// get images to process
					IJLoggerInfo("Reading", "List all images from '" + rawFolder.getName() + "'");
					def uList;
					def cMap;
					def imgMap
					try{
						(cMap, imgMap, uList) = listImages(rawFolder, compatibleFormatList)
					}catch(Exception e){
					    hasSilentlyFailed = true
						message = "An error occurred when listing images from '"+rawFolder.getAbsolutePath()+"'."
						IJLoggerError("Folder '"+rawFolder.getName()+"'", message, e)
						Map<String, String> imgSummaryMap = new HashMap<>()
						imgSummaryMap.putAll(commonSummaryMap)
						transferSummary.add(imgSummaryMap)
						continue
					}
			
					// check if there are ome-tiff images. In case, inform the user
					def values = cMap.values()
					if(values.contains(OME_TIFF) && isOmeTiff && !isOmeTiffStandalone){
						def title = "OME_TIFF Files"
						def content = "Filesets .ome.tiff files have been detected. Please, check that all and only files from the same fileset are in the same folder. "+
									"If it is not the case, ONLY IMAGES THAT ARE PART OF THE SAME FILESET ARE UPLOAD ; OTHER IMAGES WITHIN THE SAME FOLDER ARE NOT UPLOADED"
						IJLoggerWarn(title, content)
						omeTiffFilesWarning = true
					}

					def countRaw = 0
					for(File parentFolder : cMap.keySet()){
						def type = cMap.get(parentFolder)
						if(type == STANDARD || (type == OME_TIFF && isOmeTiff && isOmeTiffStandalone)){
							// upload all images
							for(File imgFile : imgMap.get(parentFolder)){
								IJLoggerInfo("*****************");
								Map<String, String> imgSummaryMap = new HashMap<>()
								imgSummaryMap.putAll(commonSummaryMap)
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
									imgSummaryMap.put(OMR_ID, ids.join(tokenSeparator))
									imgSummaryMap.put(STS, "Uploaded")
								}catch(Exception e){
									hasSilentlyFailed = true
									imgSummaryMap.put(STS, "Failed")
					    			message = "Impossible to upload this image on OMERO"						
									IJLoggerError(imgFile.getName(), message, e)
									continue
								}
								
								// link tags
								try{
									IJLoggerInfo(imgFile.getName(),"Parse image name and link tags to OMERO...")
									def tags = linkTagsToImage(user_client, ids, imgFile, rawFolder, imageTags, serieTags, folderTags)
									imgSummaryMap.put(TAG, tags.join(tokenSeparator))
								}catch(Exception e){
									hasSilentlyFailed = true
					    			message = "Impossible to link tags to this image"
									IJLoggerError(imgFile.getName(), message, e)
									continue
								}
								transferSummary.add(imgSummaryMap)
							}
						}else if(type == OME_TIFF){
							// upload only the first image as it is part of a fileset and the entire fileset will be uploaded
							def child = imgMap.get(parentFolder)
							countRaw += child.length
							
							Map<String, String> imgSummaryMap = new HashMap<>()
							imgSummaryMap.putAll(commonSummaryMap)
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
								imgSummaryMap.put(OMR_ID, ids.join(tokenSeparator))
								imgSummaryMap.put(STS, "Uploaded")
							}catch(Exception e){
								hasSilentlyFailed = true
				    			message = "Impossible to upload this image on OMERO"
								IJLoggerError(child.get(0).getName(), message, e)
								continue
							}
							
							// link tags
							try{
								IJLoggerInfo(child.get(0).getName(),"Parse image name and link tags to OMERO...")
								def tags = linkTagsToImage(user_client, ids, child.get(0), rawFolder, imageTags, serieTags, folderTags)
								imgSummaryMap.put(TAG, tags.join(tokenSeparator))
							}catch(Exception e){
								hasSilentlyFailed = true
				    			message = "Impossible to link tags to this image"
								IJLoggerError(child.get(0).getName(), message, e)
								continue
							}
							transferSummary.add(imgSummaryMap)
						}else{
							IJLoggerWarn("Supported Format","Screens are not supported : "+parentFolder.getAbsolutePath())
							Map<String, String> imgSummaryMap = new HashMap<>()
							imgSummaryMap.putAll(commonSummaryMap)
							imgSummaryMap.put(TYPE, type)
							imgSummaryMap.put(IMG_NAME, parentFolder.getName())
							imgSummaryMap.put(IMG_PATH, parentFolder.getAbsolutePath())
							imgSummaryMap.put(CMP, "Not Supported")
							transferSummary.add(imgSummaryMap)
						}
						IJLoggerInfo("*****************");
					}
					
					IJLoggerInfo("OMERO", countRaw + " files uploaded on OMERO");
					
					uList.each{
						Map<String, String> imgSummaryMap = new HashMap<>()
						imgSummaryMap.putAll(commonSummaryMap)
						imgSummaryMap.put(IMG_NAME, it.getName())
						imgSummaryMap.put(IMG_PATH, it.getAbsolutePath())
						imgSummaryMap.put(CMP, "Uncompatible")
						transferSummary.add(imgSummaryMap)
					}
				}	
			}
			
			if(hasSilentlyFailed)
				message = "The script ended with some errors. "
			else  
				message = "The upload and tagging have been successfully done. "

			if(omeTiffFilesWarning){
				hasSilentlyFailed = true
				message += "OME-TIFF files have been detected and uploaded. Please look at the logs "
			}
		}else{
			message = "The script was ended by the user. "
			endedByUser = true;
		}
		
	}catch(Exception e){
		IJLoggerError(e.toString(), "\n"+getErrorStackTraceAsString(e))
		if(!hasFailed){
			hasFailed = true
			message = "An error has occurred. Please look at the logs and the report to know where the processing has failed. "
		}
	}finally{
		// generate CSV report
		try{
			if(!endedByUser){
				IJLoggerInfo("CSV report", "Generate the CSV report...")
				generateCSVReport(transferSummary)
			}
		}catch(Exception e2){
			IJLoggerError(e2.toString(), "\n"+getErrorStackTraceAsString(e2))
			hasFailed = true
			message += "An error has occurred during csv report generation."
		}finally{
			// disconnect
			user_client.disconnect()
			IJLoggerInfo("OMERO","Disconnected from "+host)
			
			// print final popup
			if(!endedByUser){
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
 * return the project wrapper corresonding a new project or to an existing project, if it already exists
 */
def createOmeroProject(user_client, projectName){
	// if specified, create a new project on OMERO
    def project = new ProjectData();
	project.setName(projectName);
    
    // create the dataset on OMERO
    project = (ProjectData) user_client.getDm().saveAndReturnObject(user_client.getCtx(), project)
    def newId = project.getId()
    
    // create the corresponding wrapper
    def projectWrapper = user_client.getProject(newId)
	
	return projectWrapper
}


/**
 * return the dataset wrapper corresonding a new dataset or to an existing dataset, if it already exists
 */
def createOmeroDataset(user_client, projectWrapper, datasetName){
    def dataset = new DatasetData();
	dataset.setName(datasetName);
    
    // create the dataset on OMERO
    dataset = user_client.getDm().createDataset(user_client.getCtx(), dataset, projectWrapper.asProjectData())
    def newId = dataset.getId()
    
    // create the corresponding wrapper
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
def linkTagsToImage(user_client, ids, imgFile, rawFolder, imageTags, serieTags, folderTags){
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
			
			log += " :  " + filteredTags.join(tokenSeparator)
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

	def headerList = [RAW_FOL, PRJ, PRJ_ID, DST, DST_ID, IMG_NAME, IMG_PATH, CMP, STS, TYPE, OMR_ID, TAG]
	String header = headerList.join(csvSeparator)
	String statusOverallSummary = ""
	
	// get all summaries
	transferSummaryList.each{imgSummaryMap -> 
		def statusSummaryList = []
		//loop over the parameters
		headerList.each{outputParam->
			if(imgSummaryMap.containsKey(outputParam))
				statusSummaryList.add(imgSummaryMap.get(outputParam))
			else
				statusSummaryList.add("-")
		}
		
		statusOverallSummary += statusSummaryList.join(csvSeparator) + "\n"
	}
	String content = header + "\n"+statusOverallSummary
					
	// save the report
	def name = getCurrentDateAndHour()+"_Batch_import_and_tag_to_OMERO_report"
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


/**
 * 
 * Create the Dialog asking for the project and dataset
 * 
 * */
public class Dialog extends JFrame {
	
	private JComboBox<String> cmbProject;
    private JComboBox<String> cmbDataset;
    private JButton bnOk = new JButton("Finish");
    private JButton bnCancel = new JButton("Cancel");
    private JButton bnNext = new JButton("Next");
    private DefaultComboBoxModel<String> modelCmbProject;
    private DefaultComboBoxModel<String> modelCmbDataset;
    	
	Client client;
	def userId;
	def project_list;
	boolean enterPressed;
	boolean validated;
	
	String DEFAULT_PATH_KEY = "scriptDefaultDir"
	String FOL_PATH = "path";
    String OMR_PRJ = "project";
    String OMR_DST = "dataset";
    String IS_OME_TIFF = "isOmeTiff"
    String OME_TIFF_STD = "isOmeTiffStandalone"
    String IS_NEW_DST = "isNewDataset";
    String IS_DST_FLD = "isNewFromFolder";
    String IS_NEW_PRJ = "isNewProject";
    String DST_NAME = "datasetName";
    String PRJ_NAME = "projectName";
    String TAG_IMG = "tagImage";
    String TAG_SER = "tagSerie";
    String TAG_FOL = "tagFolder";
    
	File currentDir = IJ.getProperty(DEFAULT_PATH_KEY) == null ? new File("") : ((File)IJ.getProperty(DEFAULT_PATH_KEY))
	List<Map<String, String>> selectionList = new ArrayList<>()
	Map<String, List<String>> projectNewDatasets = new HashMap<>()
	
	public Dialog(user_client, project_names, project_list, userId){
		client = user_client
		this.userId = userId
		this.project_list = project_list
		
		project_names.each{
			projectNewDatasets.put(it, new ArrayList<>())
		}
		
    	def project = project_list.find{it.getName() == project_names[0]}
    	def dataset_list = project.getDatasets()
		def dataset_names = dataset_list.stream().map(DatasetWrapper::getName).collect(Collectors.toList())
		projectNewDatasets.put(project_names[0], dataset_names)
		
		myDialog(project_names)
	}
	
	// getters
	public boolean getEnterPressed(){return this.enterPressed}
	public boolean getValidated(){return this.validated}
	public List<Map<String, String>> getSelectedList(){return this.selectionList}
	
	// generate the dialog box
	public void myDialog(project_names) {
		// set general frame
		this.setTitle("Select your import location on OMERO")
	    this.setVisible(true);
	    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
	   // this.setPreferredSize(new Dimension(400, 250));
	    
	    // get the screen size
	    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        double width = screenSize.getWidth();
        double height = screenSize.getHeight();
        
        // set location in the middle of the screen
	    this.setLocation((int)((width - 400)/2), (int)((height - 250)/2));
		
		// build project combo model
		modelCmbProject = new DefaultComboBoxModel<>(project_names);
        cmbProject = new JComboBox<>(modelCmbProject);
        
        // build dataset combo model
		modelCmbDataset = new DefaultComboBoxModel<>((String[])projectNewDatasets.get(project_names[0]).sort());
        cmbDataset = new JComboBox<>(modelCmbDataset);
        cmbDataset.setEnabled(false)
		
            
        // label for tags
        JLabel tagLabel = new JLabel("TAG CONFIGURATION")
        tagLabel.setAlignmentX(LEFT_ALIGNMENT)
        
        // checkbox to tag with image name
        JCheckBox chkTagImage = new JCheckBox("Image tags");
        chkTagImage.setSelected(true);
		                
        // checkbox to tag with serie name
        JCheckBox chkTagSerie = new JCheckBox("Serie tags");
        chkTagSerie.setSelected(false);
        
        // checkbox to tag with folder name
        JCheckBox chkTagFolder = new JCheckBox("Folder tags");
        chkTagFolder.setSelected(false);
        
        // Root folder for project
        JLabel labRootFolder  = new JLabel("Folder(s) to upload");
        JTextField tfRootFolder = new JTextField();
        tfRootFolder.setColumns(15);
        tfRootFolder.setText(currentDir.getAbsolutePath())

        // button to choose root folder
        JButton bRootFolder = new JButton("Choose folder");
        bRootFolder.addActionListener(e->{
            JFileChooser directoryChooser = new JFileChooser();
            directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            directoryChooser.setCurrentDirectory(currentDir);
            directoryChooser.setMultiSelectionEnabled(true)
            directoryChooser.setDialogTitle("Choose the project folder");
            directoryChooser.showDialog(new JDialog(),"Select");

            if (directoryChooser.getSelectedFiles() != null){
                tfRootFolder.setText(directoryChooser.getSelectedFiles().join(","));
                currentDir = directoryChooser.getSelectedFile()
                IJ.setProperty(DEFAULT_PATH_KEY, currentDir)
            }
        });
        
        
                
        // Radio button to choose existing project
        JLabel extensionLabel = new JLabel("Choose image extension");
        ButtonGroup formatChoice = new ButtonGroup();
        JRadioButton rbOmetiffFormat = new JRadioButton("OME-TIFF");
        formatChoice.add(rbOmetiffFormat);
        rbOmetiffFormat.setSelected(false);
        
        // Radio button to choose new project
        JRadioButton rbOtherFormat = new JRadioButton("Other formats");
        formatChoice.add(rbOtherFormat);
        rbOtherFormat.setSelected(true);    
        
        
        // Radio button to choose new project
        ButtonGroup ometiffChoice = new ButtonGroup();
        JRadioButton rbStdOmetiff = new JRadioButton("Standalone OME-TIFF");
        ometiffChoice.add(rbStdOmetiff);
        rbStdOmetiff.setSelected(true);
        rbStdOmetiff.setEnabled(false);
        
        
        JRadioButton rbFsOmetiff = new JRadioButton("Fileset OME-TIFF");
        ometiffChoice.add(rbFsOmetiff);
        rbFsOmetiff.setSelected(false); 
		rbFsOmetiff.setEnabled(false)
        
		
        // build Combo project
        JPanel boxComboProject = new JPanel();
        JLabel projectLabel = new JLabel("Project");
        boxComboProject.add(projectLabel);
        boxComboProject.add(cmbProject);
        boxComboProject.setLayout(new FlowLayout());
        
         // Project name
        JLabel labProjectName = new JLabel("Project name");
        JTextField tfProjectName = new JTextField("");
        tfProjectName.setColumns(15);
        tfProjectName.setEnabled(false)
        
        // Radio button to choose existing project
        ButtonGroup projectChoice = new ButtonGroup();
        JRadioButton rbExistingProject = new JRadioButton("Existing project");
        projectChoice.add(rbExistingProject);
        rbExistingProject.setSelected(true);
        
        // Radio button to choose new project
        JRadioButton rbNewProject = new JRadioButton("New project");
        projectChoice.add(rbNewProject);
        rbNewProject.setSelected(false);        
        
        // build Combo dataset
        JPanel boxComboDataset = new JPanel();
        JLabel datasetLabel = new JLabel("Dataset");
        boxComboDataset.add(datasetLabel);
        boxComboDataset.add(cmbDataset);
        boxComboDataset.setLayout(new FlowLayout());
                
        // Dataset name
        JLabel labDatasetName = new JLabel("Dataset name");
        JTextField tfDatasetName = new JTextField("");
        tfDatasetName.setColumns(15);
        
        // Radio button to choose existing dataset
        ButtonGroup datasetChoice = new ButtonGroup();
        JRadioButton rbExistingDataset = new JRadioButton("Existing dataset");
        datasetChoice.add(rbExistingDataset);
        rbExistingDataset.setSelected(false);
        rbExistingDataset.addActionListener(e -> {
			cmbDataset.setEnabled(rbExistingDataset.isSelected());
			tfDatasetName.setEnabled(!rbExistingDataset.isSelected())
        });
        
         // Radio button to choose new dataset
        JRadioButton rbNewDataset = new JRadioButton("New dataset");
        datasetChoice.add(rbNewDataset);
        rbNewDataset.setSelected(true);
        rbNewDataset.addActionListener(e -> {
			cmbDataset.setEnabled(!rbNewDataset.isSelected());
			tfDatasetName.setEnabled(rbNewDataset.isSelected())
        });
        
        
         // checkbox to tag with parent folder name
        JCheckBox chkNewFromFolder = new JCheckBox("New from folder");
        chkNewFromFolder.setSelected(false);
        chkNewFromFolder.addActionListener(e -> {
			cmbDataset.setEnabled(!chkNewFromFolder.isSelected() && !rbNewDataset.isSelected());
			tfDatasetName.setEnabled(!chkNewFromFolder.isSelected() && !rbExistingDataset.isSelected())
			rbExistingDataset.setEnabled(!chkNewFromFolder.isSelected() && !rbNewProject.isSelected())
			rbNewDataset.setEnabled(!chkNewFromFolder.isSelected())
        });
        
        // actionListener on new project radio button
        rbNewProject.addActionListener(e -> {
			cmbProject.setEnabled(!rbNewProject.isSelected());
			tfProjectName.setEnabled(rbNewProject.isSelected())
			rbExistingDataset.setSelected(!rbNewProject.isSelected());
			rbNewDataset.setSelected(rbNewProject.isSelected());
			rbExistingDataset.setEnabled(!rbNewProject.isSelected());
			tfDatasetName.setEnabled(rbNewProject.isSelected() && !chkNewFromFolder.isSelected())
			cmbDataset.setEnabled(!rbNewProject.isSelected());
        });
        
        // actionListener on existing project radio button
        rbExistingProject.addActionListener(e -> {
			cmbProject.setEnabled(rbExistingProject.isSelected());
			tfProjectName.setEnabled(!rbExistingProject.isSelected())
			rbExistingDataset.setSelected(!rbExistingProject.isSelected());
			rbNewDataset.setSelected(rbExistingProject.isSelected());
			rbExistingDataset.setEnabled(rbExistingProject.isSelected() && !chkNewFromFolder.isSelected());
        });
        
         // actionListener on ome tiff extension radio button
        rbOmetiffFormat.addActionListener(e -> {
			rbStdOmetiff.setEnabled(rbOmetiffFormat.isSelected());
			rbFsOmetiff.setEnabled(rbOmetiffFormat.isSelected())
        });
        
        // actionListener on otehr extension radio button
        rbOtherFormat.addActionListener(e -> {
			rbStdOmetiff.setEnabled(!rbOtherFormat.isSelected());
			rbFsOmetiff.setEnabled(!rbOtherFormat.isSelected())
        });
              
        // build buttons
        JPanel boxButton = new JPanel();
        boxButton.add(bnNext);
        boxButton.add(bnOk);
        boxButton.add(bnCancel);
        boxButton.setLayout(new FlowLayout());
        
        // radioBox Dataset
        JPanel windowRadioDataset = new JPanel();
        windowRadioDataset.setLayout(new BoxLayout(windowRadioDataset, BoxLayout.X_AXIS));
        windowRadioDataset.add(rbExistingDataset);
        windowRadioDataset.add(rbNewDataset);
        windowRadioDataset.add(chkNewFromFolder);
        
         // radioBox Project
        JPanel windowRadioProject = new JPanel();
        windowRadioProject.setLayout(new BoxLayout(windowRadioProject, BoxLayout.X_AXIS));
        windowRadioProject.add(rbExistingProject);
        windowRadioProject.add(rbNewProject);
        
        // Dataset Name Box
        JPanel windowDataset = new JPanel();
        windowDataset.setLayout(new BoxLayout(windowDataset, BoxLayout.X_AXIS));
        windowDataset.add(labDatasetName);
        windowDataset.add(tfDatasetName);
        
        // Project Name Box
        JPanel windowProject = new JPanel();
        windowProject.setLayout(new BoxLayout(windowProject, BoxLayout.X_AXIS));
        windowProject.add(labProjectName);
        windowProject.add(tfProjectName);
        
         // Folder box
        JPanel windowFolder = new JPanel();
        windowFolder.setLayout(new BoxLayout(windowFolder, BoxLayout.X_AXIS));
        windowFolder.add(labRootFolder);
        windowFolder.add(tfRootFolder);
        windowFolder.add(bRootFolder);
        
        JPanel windowExtension = new JPanel();
        windowExtension.setLayout(new BoxLayout(windowExtension, BoxLayout.X_AXIS));
        windowExtension.add(extensionLabel);
        windowExtension.add(rbOmetiffFormat);
        windowExtension.add(rbOtherFormat);
        
        JPanel windowOmeTiff = new JPanel();
        windowOmeTiff.setLayout(new BoxLayout(windowOmeTiff, BoxLayout.X_AXIS));
        windowOmeTiff.add(rbStdOmetiff);
        windowOmeTiff.add(rbFsOmetiff);
        
        // general panel
        JPanel windowNLGeneral = new JPanel();
        windowNLGeneral.setLayout(new BoxLayout(windowNLGeneral, BoxLayout.Y_AXIS));
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowFolder);
        windowNLGeneral.add(windowExtension);
        windowNLGeneral.add(windowOmeTiff);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowRadioProject);
        windowNLGeneral.add(boxComboProject);
        windowNLGeneral.add(windowProject);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(windowRadioDataset);
        windowNLGeneral.add(boxComboDataset);
        windowNLGeneral.add(windowDataset);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        windowNLGeneral.add(tagLabel);
		windowNLGeneral.add(chkTagImage);
        windowNLGeneral.add(chkTagSerie);
        windowNLGeneral.add(chkTagFolder);
        windowNLGeneral.add(new JSeparator());
        windowNLGeneral.add(boxButton);
        windowNLGeneral.add(Box.createRigidArea(new Dimension(0,5)));
        
        JPanel nicerWindow = new JPanel();
        nicerWindow.setLayout(new BoxLayout(nicerWindow, BoxLayout.X_AXIS));
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        nicerWindow.add(windowNLGeneral);
        nicerWindow.add(Box.createRigidArea(new Dimension(5,0)));
        
        // add listener on project combo box
        cmbProject.addItemListener(
			new ItemListener(){
				    @Override
			    public void itemStateChanged(ItemEvent e) {
					// get the datasets corresponding to the selected project
			        def chosen_project = (String) cmbProject.getSelectedItem()
			        def dataset_names
			        if(projectNewDatasets.get(chosen_project).isEmpty()){
			        	def project = project_list.find{it.getName() == chosen_project}
						def dataset_list = project.getDatasets()
						dataset_names = dataset_list.stream().map(DatasetWrapper::getName).collect(Collectors.toList()).sort()
						projectNewDatasets.put(chosen_project, dataset_names)
			        }else{
			        	dataset_names = projectNewDatasets.get(chosen_project)
			        }
			        
			        // update the dataset combo box
					modelCmbDataset.removeAllElements();
        			for (String dataset : dataset_names) modelCmbDataset.addElement(dataset);
        			cmbDataset.setSelectedIndex(0);
			    }
			}
		);
		
		// add listener on Ok and Cancel button
		bnOk.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				
    				def rootFolder = (String)tfRootFolder.getText()
    				if(rootFolder != null && !rootFolder.isEmpty()){
						
						if(!checkInputs(tfRootFolder, rbNewDataset, chkNewFromFolder, tfDatasetName, rbNewProject, tfProjectName))
							return
		
						Map<String, String> selection = new HashMap<>()
						selection.put(FOL_PATH, rootFolder)
						selection.put(IS_OME_TIFF, String.valueOf(rbOmetiffFormat.isSelected()))
						selection.put(OME_TIFF_STD, String.valueOf(rbStdOmetiff.isSelected()))
						selection.put(OMR_PRJ, (String) cmbProject.getSelectedItem())
						selection.put(OMR_DST, (String) cmbDataset.getSelectedItem())
						selection.put(IS_NEW_DST, String.valueOf(rbNewDataset.isSelected()))
						selection.put(IS_DST_FLD, String.valueOf(chkNewFromFolder.isSelected()))
						selection.put(IS_NEW_PRJ, String.valueOf(rbNewProject.isSelected()))
						selection.put(DST_NAME, (String) tfDatasetName.getText())
						selection.put(PRJ_NAME, (String) tfProjectName.getText())
						selection.put(TAG_IMG, String.valueOf(chkTagImage.isSelected()))
						selection.put(TAG_SER, String.valueOf(chkTagSerie.isSelected()))
						selection.put(TAG_FOL, String.valueOf(chkTagFolder.isSelected()))
						selectionList.add(selection)
    				}

    				enterPressed = true
    				validated = true;
    				
    				this.dispose()
    			}
			}
		)
		
		bnCancel.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				enterPressed = true
    				validated = false;
    				this.dispose()
    			}
			}
		)
		
		bnNext.addActionListener(
			new ActionListener(){
				@Override
    			public void actionPerformed(ActionEvent e) {
    				if(!checkInputs(tfRootFolder, rbNewDataset, chkNewFromFolder, tfDatasetName, rbNewProject, tfProjectName))
    					return
    				
    				Map<String, String> selection = new HashMap<>()
    				selection.put(FOL_PATH, (String) tfRootFolder.getText())
					selection.put(IS_OME_TIFF, String.valueOf(rbOmetiffFormat.isSelected()))
					selection.put(OME_TIFF_STD, String.valueOf(rbStdOmetiff.isSelected()))
    				selection.put(OMR_PRJ, (String) cmbProject.getSelectedItem())
    				selection.put(OMR_DST, (String) cmbDataset.getSelectedItem())
					selection.put(IS_NEW_DST, String.valueOf(rbNewDataset.isSelected()))
					selection.put(IS_DST_FLD, String.valueOf(chkNewFromFolder.isSelected()))
					selection.put(IS_NEW_PRJ, String.valueOf(rbNewProject.isSelected()))
					selection.put(DST_NAME, (String) tfDatasetName.getText())
					selection.put(PRJ_NAME, (String) tfProjectName.getText())
					selection.put(TAG_IMG, String.valueOf(chkTagImage.isSelected()))
					selection.put(TAG_SER, String.valueOf(chkTagSerie.isSelected()))
					selection.put(TAG_FOL, String.valueOf(chkTagFolder.isSelected()))
					selectionList.add(selection)
										
					if(rbNewProject.isSelected()){
						def prjName = (String) tfProjectName.getText()
						projectNewDatasets.put(prjName, new ArrayList<>())
	        			modelCmbProject.addElement(prjName);
					}
					
					if(rbNewDataset.isSelected() && !chkNewFromFolder.isSelected()){
						def dstName = (String) tfDatasetName.getText()
						def chosenProject = (String) cmbProject.getSelectedItem()
						if(rbNewProject.isSelected())
							chosenProject = (String) tfProjectName.getText()
							
						def tmpDatasetList = projectNewDatasets.get(chosenProject)
						tmpDatasetList.add(dstName)
						projectNewDatasets.put(chosenProject, tmpDatasetList)
						
						// update the dataset combo box
						modelCmbDataset.removeAllElements();
	        			for (String dataset : tmpDatasetList) modelCmbDataset.addElement(dataset);
	        			cmbDataset.setSelectedIndex(0);
					}
					
					tfDatasetName.setText("");
					tfProjectName.setText("");
					tfRootFolder.setText("");
    			}
			}
		)
		
		 // set main interface parameters
		this.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }

            @Override
            public void windowClosing(WindowEvent e) {

            }

            @Override
            public void windowClosed(WindowEvent e) {
				enterPressed = true
    			validated = false;
            }

            @Override
            public void windowIconified(WindowEvent e) {

            }

            @Override
            public void windowDeiconified(WindowEvent e) {

            }

            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {

            }
        });

        this.setContentPane(nicerWindow);
        this.pack();
    }
    
    private boolean checkInputs(tfRootFolder, rbNewDataset, chkNewFromFolder, tfDatasetName, rbNewProject, tfProjectName){
    	if(tfRootFolder.getText() == null || tfRootFolder.getText().isEmpty()){
    		def message = "You must enter a folder to process"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
    	}
    	
    	if(rbNewDataset.isSelected() && !chkNewFromFolder.isSelected() && (tfDatasetName.getText() == null || tfDatasetName.getText().isEmpty())){
			def message = "You must enter a name for the new dataset"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
		}
		
		if(rbNewProject.isSelected() && (tfProjectName.getText() == null || tfProjectName.getText().isEmpty())){
			def message = "You must enter a name for the new project"
			JOptionPane.showMessageDialog(null, message, "ERROR", JOptionPane.ERROR_MESSAGE);
			return false
		}
		
		return true			
    }
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


import java.awt.AWTEvent;
import java.util.stream.Collectors
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.*;
import java.awt.FlowLayout;
import javax.swing.BoxLayout
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.Dimension;
import java.awt.Toolkit;