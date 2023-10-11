"""
 MIF/Retrieve_images_from_HRM.py
 Retrieve images from HRM-Share folder to OMERO with parameters and log file.
-----------------------------------------------------------------------------
  Copyright (C) 2023
  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  You should have received a copy of the GNU General Public License along
  with this program; if not, write to the Free Software Foundation, Inc.,
  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
------------------------------------------------------------------------------
Created by Rémy Dornier, based on the work of Niko Ehrenfeuchter from IMCF, Basel (https://github.com/imcf/hrm-omero)
"""

import os
import re

import omero
from bs4 import BeautifulSoup
import omero.scripts as scripts
import omero.model as model
from omero.gateway import BlitzGateway
from omero.gateway import DatasetWrapper
from omero.gateway import MapAnnotationWrapper
from omero.gateway import TagAnnotationWrapper
from omero.rtypes import rstring
from omero.plugins.sessions import SessionsControl
from omero.cli import CLI
import tempfile
from datetime import date
import yaml
from importlib import import_module

ImportControl = import_module("omero.plugins.import").ImportControl


SERVER_PARAM_NAME = "OMERO_server"
PORT_PARAM_NAME = "Port"
DELETE_DECONVOLVED_PARAM_NAME = "Delete_deconvolved_images_on_HRM"
DELETE_RAW_PARAM_NAME = "Delete_raw_images_on_HRM"

# ********************* All the following methods are taken from https://github.com/imcf/hrm-omero ****************


def to_omero(conn, cli, host, port, dataset_id, image_file, omero_logfile="", _fetch_zip_only=False):
    """Upload an image into a specific dataset in OMERO.
    In case we know from the suffix that a given  format is not supported by OMERO, the
    upload will not be initiated at all (e.g. for SVI-HDF5, having the suffix '.h5').
    The import itself is done by instantiating the CLI class, assembling the required
    arguments, and finally running `cli.invoke()`. This eventually triggers the
    `importer()` method defined in [OMERO's Python bindings][1].
    [1]: https://github.com/ome/omero-py/blob/master/src/omero/plugins/import.py
    Parameters
    ----------
    conn : omero.gateway.BlitzGateway
        The OMERO connection object.
    cli: omero.cli.CLI object
        command line object to upload images on the server
    host: String
        current hostname address
    port: int
        the OMERO communication port
    dataset_id : hrm_omero.misc.OmeroId
        The ID of the target dataset in OMERO.
    image_file : str
        The local image file including the full path.
    omero_logfile : str, optional
        The prefix of files to be used to capture OMERO's `import` call stderr messages.
        If the parameter is non-empty the `--debug ALL` option will be added to the
        `omero` call with the output being placed in the specified file. If the
        parameter is omitted or empty, debug messages will be disabled.
    _fetch_zip_only : bool, optional
        Replaces all parameters to the import call by `--advanced-help`, which is
        **intended for INTERNAL TESTING ONLY**. No actual import will be attempted!
    Returns
    -------
    hrm_omero.misc.OmeroId
        The ID of the newly imported image, None otherwise.
    Raises
    ------
    TypeError
        Raised in case `image_file` is in a format that is not supported by OMERO.
    ValueError
        Raised in case `omero_id` is not pointing to a dataset.
    """

    # TODO: revisit this, as e.g. BDV .h5 files are supported for now!
    # if image_file.lower().endswith((".h5", ".hdf5")):
    #    msg = f"ERROR importing [{image_file}]: HDF5 format not supported by OMERO!"
    #    print(msg)
    #   raise TypeError(msg)

    if dataset_id.obj_type != "Dataset":
        msg = "Currently only the upload to 'Dataset' objects is supported!"
        print(msg)
        raise ValueError(msg)

    # we have to create the annotations *before* we actually upload the image
    # data itself and link them to the image during the upload - the other way
    # round is not possible right now as the CLI wrapper (see below) doesn't
    # expose the ID of the newly created object in OMERO (confirmed by J-M and
    # Sebastien on the 2015 OME Meeting):
    #### namespace = "deconvolved.hrm"
    #### mime = 'text/plain'
    #### annotations = []
    #### # TODO: the list of suffixes should not be hardcoded here!
    #### for suffix in ['.hgsb', '.log.txt', '.parameters.txt']:
    ####     if not os.path.exists(basename + suffix):
    ####         continue
    ####     ann = conn.createFileAnnfromLocalFile(
    ####         basename + suffix, mimetype=mime, ns=namespace, desc=None)
    ####     annotations.append(ann.getId())

    # currently there is no direct "Python way" to import data into OMERO, so we have to
    # use the CLI wrapper for this...
    # TODO: check the more recent code mentioned by the OME developers in the forum
    # thread: https://forum.image.sc/t/automated-uploader-to-omero-in-python/38290
    # https://gitlab.com/openmicroscopy/incubator/omero-python-importer/-/blob/master/import.py)
    # and also see https://pypi.org/project/omero-upload/

    # modified from Niko's job
    import_args = ["import",
                   '-k', str(conn._getSessionId()),
                   '-s', host,
                   '-p', str(port),
                   "--skip", "upgrade"
                   # disable upgrade checks (https://forum.image.sc/t/unable-to-use-cli-importer/26424)
                   ]

    if omero_logfile:
        print("WARNING", f"Messages (stderr) from import will go to [{omero_logfile}].")
        import_args.extend(["--debug", "ALL"])
        import_args.extend(["--errs", omero_logfile])

    import_args.extend(["-d", dataset_id.obj_id])

    # capture stdout and request YAML format to parse the output later on:
    tempdir = tempfile.TemporaryDirectory(prefix="hrm-omero__")
    cap_stdout = f"{tempdir.name}/omero-import-stdout"
    print("DEBUG", f"Capturing stdout of the 'omero' call into [{cap_stdout}]...")
    import_args.extend(["--file", cap_stdout])
    import_args.extend(["--output", "yaml"])

    #### for ann_id in annotations:
    ####     import_args.extend(['--annotation_link', str(ann_id)])
    import_args.append(image_file)
    if _fetch_zip_only:
        # calling 'import --advanced-help' will trigger the download of OMERO.java.zip
        # in case it is not yet present (the extract_image_id() call will then fail,
        # resulting in the whole function returning "False")
        print("WRANING", "As '_fetch_zip_only' is set NO IMPORT WILL BE ATTEMPTED!")
        import_args = ["import", "--advanced-help"]
    print("DEBUG", f"import_args: {import_args}")
    try:
        cli.invoke(import_args, strict=True)
        cli.get_client().closeSession()  # force killing the session
        #cli.close() # see if it doesn't crash
        imported_id = extract_image_id(cap_stdout)
        print("SUCCESS", f"Imported OMERO image ID: {imported_id}")
    except PermissionError as err:
        print("ERROR", err)
        omero_userdir = os.environ.get("OMERO_USERDIR", "<not-set>")
        print("ERROR", f"Current OMERO_USERDIR value: {omero_userdir}")
        print("ERROR",
              (
                  "Please make sure to read the documentation about the 'OMERO_USERDIR' "
                  "environment variable and also check if the file to be imported has "
                  "appropriate permissions!"
              ),
              )
        return None
    except Exception as err:  # pylint: disable-msg=broad-except
        print("ERROR", f"ERROR: uploading '{image_file}' to {dataset_id} failed!")
        print("ERROR", f"OMERO error message: >>>{err}<<<")
        print("WARNING", f"import_args: {import_args}")
        return None
    finally:
        tempdir.cleanup()

    return OmeroId(f"G:{dataset_id.group}:Image:{imported_id}")  # modify from Niko's job


def attach_log_file(conn, target_id, image_file):
    """Add an txt file as attachement to an OMERO object.
    Parameters
    ----------
    conn : omero.gateway.BlitzGateway
        The OMERO connection object.
    target_id : hrm_omero.misc.OmeroId
        The ID of the OMERO object that should receive the annotation.
    image_file : str
        The path to the image file.
    """
    # get omero object
    omero_object = conn.getObject(target_id.obj_type, target_id.obj_id)

    # get the file to attach
    suffix = ".log.txt"
    if not image_file.endswith(suffix):
        candidate = parse_job_basename(image_file) + suffix
        if os.path.exists(candidate):
            print("DEBUG", f"Found [{candidate}], will use it instead of [{image_file}].")
            file_to_upload = candidate
        else:
            print("ERROR", f"The file {candidate} does not exists")
            return
    else:
        file_to_upload = image_file

    print("DEBUG", f"Trying attach [{file_to_upload}] file to {target_id.obj_type} {target_id.obj_id}...")

    # create the original file and file annotation (uploads the file etc.)
    namespace = "hrm.deconvolution.log"
    print("\nCreating an OriginalFile and FileAnnotation")
    file_ann = conn.createFileAnnfromLocalFile(
        file_to_upload, mimetype="text/plain", ns=namespace, desc=None)

    # attach the file to the object
    print("Attaching FileAnnotation to Dataset: ", "File ID:", file_ann.getId(),
          ",", file_ann.getFile().getName(), "Size:", file_ann.getFile().getSize())
    omero_object.linkAnnotation(file_ann)


def extract_image_id(fname):
    """Parse the YAML returned by an 'omero import' call and extract the image ID.
    Parameters
    ----------
    fname : str
        The path to the `yaml` file to parse.
    Returns
    -------
    int or None
        The OMERO ID of the newly imported image, e.g. `1568386` or `None` in case
        parsing the file failed for any reason.
    """
    try:
        with open(fname, "r", encoding="utf-8") as stream:
            parsed = yaml.safe_load(stream)
        if len(parsed[0]["Image"]) != 1:
            msg = f"Unexpected YAML retrieved from OMERO, unable to parse:\n{parsed}"
            print("ERROR", msg)
            raise SyntaxError(msg)
        image_id = parsed[0]["Image"][0]
    except Exception as err:  # pylint: disable-msg=broad-except
        print("ERROR", f"Error parsing imported image ID from YAML output: {err}")
        return None

    print("SUCCESS", f"Successfully parsed Image ID from YAML: {image_id}")
    return image_id


def add_annotation_key_value(conn, omero_id_obj, annotation):
    """Add a key-value "map" annotation to an OMERO object.
    Parameters
    ----------
    conn : omero.gateway.BlitzGateway
        The OMERO connection object.
    omero_id_obj : hrm_omero.misc.OmeroId
        The ID of the OMERO object that should receive the annotation.
    annotation : dict(dict)
        The map annotation as returned by `hrm_omero.hrm.parse_summary()`.
    Returns
    -------
    bool
        True in case of success, False otherwise.
    Raises
    ------
    RuntimeError
        Raised in case re-establishing the OMERO connection fails.
    """
    print("TRACE", f"Adding a map annotation to {omero_id_obj}")

    if omero_id_obj is None:
        print("ERROR", f"{omero_id_obj} is not a valid ID in OMERO!")
        return False

    target_obj = conn.getObject(omero_id_obj.obj_type, omero_id_obj.obj_id)
    if target_obj is None:
        print("ERROR", f"Unable to identify target object {omero_id_obj.obj_id} in OMERO!")
        return False

    for section in annotation:
        namespace = f"Huygens Remote Manager - {section}"
        map_ann = MapAnnotationWrapper(conn)
        map_ann.setValue(annotation[section].items())
        map_ann.setNs(namespace)
        map_ann.save()
        target_obj.linkAnnotation(map_ann)
        print("DEBUG", f"Added key-value annotation using namespace [{namespace}].")

    print("SUCCESS", f"Added annotation to {target_obj.getId()} : {annotation}")

    return True


def add_tags(conn, target_img_id_obj, dataset_id_obj):
    """Add tags annotation to an OMERO object.
    Parameters
    ----------
    conn : omero.gateway.BlitzGateway
        The OMERO connection object.
    target_img_id_obj : hrm_omero.misc.OmeroId
        The ID of the OMERO object that should receive the annotation.
    dataset_id_obj: hrm_omero.misc.OmeroId
        The ID of the target dataset
    Returns
    -------
    bool
        True in case of success, False otherwise.
    """
    print("TRACE", f"Adding a tags to {target_img_id_obj}")

    if target_img_id_obj is None:
        print("ERROR", f"{target_img_id_obj} is not a valid ID in OMERO!")
        return False

    target_img_obj = conn.getObject(target_img_id_obj.obj_type, target_img_id_obj.obj_id)
    if target_img_obj is None:
        print("ERROR", f"Unable to identify target object {target_img_id_obj.obj_id} in OMERO!")
        return False

    deconvolved_img_name = target_img_obj.getName()
    dataset_obj = conn.getObject(dataset_id_obj.obj_type, dataset_id_obj.obj_id)
    img_base_name = parse_image_basename(deconvolved_img_name)
    raw_img_obj = None
    print("INFO", f"Image base name :  {img_base_name}")

    # get the raw image
    for dataset_image_obj in dataset_obj.listChildren():
        if (img_base_name in dataset_image_obj.getName()) and (not (dataset_image_obj.getName() == deconvolved_img_name)):
            raw_img_obj = dataset_image_obj
            break

    raw_tag_value = "raw"
    deconvolved_tag_value = "deconvolved"
    hrm_tag_value = "hrm"

    if raw_img_obj is not None:
        # get all tags from the raw image
        raw_img_tag_obj_list = []

        # list all tag from the raw image
        for ann in raw_img_obj.listAnnotations():
            if ann.OMERO_TYPE == omero.gateway.TagAnnotationI:
                raw_img_tag_obj_list.append(ann)

        # transfer tags from raw image to deconvolved image
        raw_img_tag_value_list = []
        for raw_img_tag_obj in raw_img_tag_obj_list:
            # remove the tag "raw" that should be specific to raw images
            if not raw_img_tag_obj.getTextValue().lower() == raw_tag_value.lower():
                target_img_obj.linkAnnotation(raw_img_tag_obj)
                raw_img_tag_value_list.append(raw_img_tag_obj.getTextValue())
        print("INFO", f"Transfer the following tags from raw to deconvolved image : {raw_img_tag_value_list}")

        # create raw tag
        tag_list = [raw_tag_value, hrm_tag_value]
        print("INFO", f"Adding the following tag to the raw image : {tag_list}")
        check_existence_and_add_tag_objs(conn, tag_list, raw_img_obj, raw_img_tag_obj_list)

    # create deconvolved tag
    tag_list = [deconvolved_tag_value, hrm_tag_value]
    print("INFO", f"Adding the following tag to the deconvolved image : {tag_list}")
    check_existence_and_add_tag_objs(conn, tag_list, target_img_obj)

    return True


def check_existence_and_add_tag_objs(conn, tag_value_list, target_obj, reference_tag_obj_list=None):
    """Add tags annotation to an OMERO object.
    Parameters
    ----------
    conn : omero.gateway.BlitzGateway
        The OMERO connection object.
    tag_value_list : List of string
        tags to add to the target object
    target_obj: omero.model.DataObject
        The target object on OMERO
    reference_tag_obj_list: List of omero.model.TagAnnotationI objects
        Tags that are already linked to the target object
    """
    if reference_tag_obj_list is None:
        reference_tag_obj_list = []

    # list all available tags
    group_tag_obj_list = conn.getObjects('TagAnnotation')
    # loop over the tags to link
    for tag_value in tag_value_list:
        new_tag_obj = None
        # check if the current tag already exists in the DB
        for group_tag_obj in group_tag_obj_list:
            if tag_value.lower() == group_tag_obj.getTextValue().lower():
                new_tag_obj = group_tag_obj
                print("INFO", f"Tag {tag_value.lower()} already exists in the DB")
                break
        # if the tag doesn't exist yet, create it
        if new_tag_obj is None:
            print("INFO", f"Tag {tag_value.lower()} doesn't exist in the DB ; create it")
            new_tag_obj = TagAnnotationWrapper(conn)
            new_tag_obj.setValue(tag_value)
            new_tag_obj.save()

        to_link = True
        for reference_tag_obj in reference_tag_obj_list:
            # if the tag is already link to the target, don't link it twice
            if reference_tag_obj.getTextValue().lower() == new_tag_obj.getTextValue().lower():
                to_link = False
                print("INFO", f"Tag {tag_value.lower()} already linked to {type(target_obj)} : {target_obj.getId()}")
                break
        # link the tag
        if to_link:
            print("INFO", f"Link tag {tag_value.lower()} to {type(target_obj)} : {target_obj.getId()}")
            target_obj.linkAnnotation(new_tag_obj)


def parse_summary(fname):
    """Parse the job parameter summary generated by HRM into a dict.
    Parse the HTML file generated by the HRM containing the parameter summary and
    generate a nested dict from it. The HTML file is assumed to contain three `<table>`
    items that contain a single `<td class="header">` item with the title and a `<tr>`
    section with four `<td>` items per parameter (being *parameter-name*, *channel*,
    *source* and *value*), e.g. something of this form:
    ```
    _____________________________________________
    |___________________title___________________|
    |_________________(ignored)_________________|
    | parameter-name | channel | source | value |
    ...
    | parameter-name | channel | source | value |
    ---------------------------------------------
    ```
    Parameters
    ----------
    fname : str
        The filename of the job's HTML parameter summary or (e.g.) the resulting image
        file. In case `fname` doesn't end in the common parameter summary suffix (for
        example if the image file name was provided), the function tries to derive the
        name of summary file and use that one for parsing.
    Returns
    -------
    dict(dict)
        A dict with the parsed section names (table titles) being the keys, each
        containing another dict with the parameter names as keys (including the channel
        unless the parameter is channel-independent). See the example below.
    Example
    -------
    >>> parse_summary('image_001.parameters.txt')
    ... {
    ...     "Image Parameters": {
    ...         "Emission wavelength (nm) [ch:0]": "567.000",
    ...         "Excitation wavelength (nm) [ch:0]": "456.000",
    ...         "Lens refractive index [ch:0]": "4.567",
    ...         "Microscope type [ch:0]": "widefield",
    ...         "Numerical aperture [ch:0]": "2.345",
    ...         "Point Spread Function": "theoretical",
    ...         "Sample refractive index [ch:0]": "3.456",
    ...         "Time interval (s)": "1.000000",
    ...         "X pixel size (μm)": "0.123456",
    ...         "Y pixel size (μm)": "0.123456",
    ...         "Z step size (μm)": "0.234567",
    ...     },
    ...     "Restoration Parameters": {
    ...         "Autocrop": "no",
    ...         "Background estimation": "auto",
    ...         "Deconvolution algorithm": "iiff",
    ...         "Number of iterations": "42",
    ...         "Quality stop criterion": "0.000007",
    ...         "Signal/Noise ratio [ch:0]": "99",
    ...     },
    ... }
    """
    # In case `fname` doesn't end with the common suffix for job summary files check if
    # it is the actual *image* filename of an HRM job and try to use the corresponding
    # parameter summary file instead:
    suffix = ".parameters.txt"
    if not fname.endswith(suffix):
        candidate = parse_job_basename(fname) + ".parameters.txt"
        if os.path.exists(candidate):
            print("DEBUG", f"Found [{candidate}], will use it instead of [{fname}].")
            fname = candidate
    print("DEBUG", f"Trying to parse job parameter summary file [{fname}]...")

    try:
        with open(fname, "r", encoding="utf-8") as soupfile:
            soup = BeautifulSoup(soupfile, features="html.parser")
            print("TRACE", f"BeautifulSoup successfully parsed [{fname}].")
    except IOError as err:
        print("ERROR", f"Unable to open parameter summary file [{fname}]: {err}")
        return None
    except Exception as err:  # pragma: no cover  # pylint: disable-msg=broad-except
        print("ERROR", f"Parsing summary file [{fname}] failed: {err}")
        return None

    sections = {}  # job parameter summaries have multiple sections split by headers
    rows = []
    for table in soup.findAll("table"):
        print("TRACE", "Parsing table header...")
        try:
            rows = table.findAll("tr")
            header = rows[0].findAll("td", class_="header")[0].text
        except Exception:  # pylint: disable-msg=broad-except
            print("DEBUG", "Skipping table entry that doesn't have a header.")
            continue
        print("TRACE", f"Parsed table header: {header}")
        if header in sections:
            raise KeyError(f"Error parsing parameters, duplicate header: {header}")

        pairs = {}
        # and the table body, starting from the 3rd <tr> item:
        for row in rows[2:]:
            cols = row.findAll("td")
            # parse the parameter "name":
            param_key = cols[0].text
            print("TRACE", f"Parsed (raw) key name: {param_key}")
            # replace HTML-encoded chars:
            param_key = param_key.replace("&mu;m", "µm")

            # parse the channel and add it to the key-string (unless it's "All"):
            channel = cols[1].text
            if channel == "All":
                channel = ""
            else:
                channel = f" [ch:{channel}]"
            param_key += channel

            # parse the parameter value:
            param_value = cols[3].text

            # finally add a new entry to the dict unless the key already exists:
            if param_key in pairs:
                raise KeyError(f"Parsing failed, duplicate parameter: {param_key}")
            pairs[param_key] = param_value
        sections[header] = pairs

    print("SUCCESS", f"Processed {len(rows)} table rows.")
    return sections


def parse_job_basename(file_name):
    """Parse the basename from an HRM job result file name.
    HRM job IDs are generated via PHP's `uniqid()` call that is giving a 13-digit
    hexadecimal string (8 digits UNIX time and 5 digits microseconds). The HRM labels its
    result files by appending an underscore (`_`) followed by this ID and an `_hrm`
    suffix. This function tries to match this section and remove everything *after* it
    from the name.
    Its intention is to safely remove the suffix from an image file name while taking no
    assumptions about how the suffix looks like (could e.g. be `.ics`, `.ome.tif` or
    similar).
    Parameters
    ----------
    file_name : str
        The input string, usually the name of an HRM result file (but any string is
        accepted).
    Returns
    -------
    str
        The input string (`file_name`) where everything *after* an HRM-like job label (e.g.
        `_abcdef0123456_hrm` or `_f435a27b9c85e_hrm`) is removed. In case the input
        string does *not* contain a matching section it is returned
    """
    print("TRACE", f"parse_job_basename - full name : {file_name}")
    basename = re.sub(r"(_[0-9a-f]{13}_hrm)\..*", r"\1", file_name)
    print("TRACE", f"parse_job_basename - HRM base name : {basename}")

    return basename


def parse_image_basename(file_name):
    """Parse the basename of the image from the HRM job IDs name. It removes all characters
    after
    Parameters
    ----------
    file_name : str
        The input string, usually the name of an HRM result file
    Returns
    -------
    str
        The input string (`file_name`) where everything *after* an HRM-like job label (e.g.
        `_abcdef0123456_hrm` or `_f435a27b9c85e_hrm`) is removed, including the job name itself. It
        only remains the raw image name without the original extension.
    """
    print("TRACE", f"parse_image_basename - full name : {file_name}")
    hrm_name = re.search(r"(_[0-9a-f]{13}_hrm)\..*", file_name)
    print("TRACE", f"parse_image_basename - HRM job name : {hrm_name.group(0)}")
    basename = file_name.replace(hrm_name.group(0), "")
    print("TRACE", f"parse_image_basename - Raw image basename : {basename}")

    return basename


class OmeroId:
    """Representation of a (group-qualified) OMERO object ID.
    The purpose of this class is to facilitate parsing and access of the
    ubiquitious target IDs denoting objects in OMERO. The constructor takes
    the common string of the form `G:[gid]:[type]:[iid]` as an input and sets
    the properties `group`, `obj_type` and `obj_id` accordingly after validating
    their contents for having reasonable values.
    Attributes
    ----------
    group : str
        The OMERO group ID as an int-like `str`.
    obj_type : str
        The OMERO object type, e.g. `Experimenter`, `Image`, ...
    obj_id : str
        The OMERO object ID as an int-like `str`.
    """

    def __init__(self, id_str):
        self.group = None
        self.obj_type = None
        self.obj_id = None
        self.parse_id_str(id_str)

    def parse_id_str(self, id_str):
        """Parse and validate an ID string of the form `G:[gid]:[type]:[oid]`
        The method will parse the given string and set the object's `group`, `obj_type`
        and `obj_id` values accordingly. In case for `id_str` the special value `ROOT`
        was supplied, `group` and `obj_id` will be set to `-1` whereas `obj_type` will
        be set to `BaseTree`.
        Parameters
        ----------
        id_str : str
            The ID of an OMERO object, e.g.
            * `G:23:Image:42`
            * `G:4:Dataset:765487`
            * special case `ROOT`, same as `G:-1:BaseTree:-1`
        Raises
        ------
        ValueError
            Raised in case a malformed `id_str` was given.
        """
        print("TRACE", f"Parsing ID string: [{id_str}]")
        if id_str == "ROOT":
            self.group = -1
            self.obj_type = "BaseTree"
            self.obj_id = -1
            print("DEBUG", f"Converted special ID 'ROOT' to [{str(self)}].")
            return

        try:
            group_type, group_id, obj_type, obj_id = id_str.split(":")
            int(group_id)  # raises a TypeError if cast to int fails
            int(obj_id)  # raises a TypeError if cast to int fails
            if group_type != "G":
                raise ValueError(f"Invalid group qualifier '{group_type}'.")
            if obj_type not in [
                "Image",
                "Dataset",
                "Project",
                "Experimenter",
                "ExperimenterGroup",
            ]:
                raise ValueError(f"Invalid object type '{obj_type}'.")
            if int(obj_id) < 1:
                raise ValueError(f"Invalid object ID '{obj_id}'.")
        except (ValueError, TypeError) as err:
            # pylint: disable-msg=raise-missing-from
            msg = f"Malformed id_str '{id_str}', expecting `G:[gid]:[type]:[oid]`."
            raise ValueError(msg, err)

        print("DEBUG", f"Validated ID string: group={group_id}, {obj_type}={obj_id}")
        self.group = group_id
        self.obj_type = obj_type
        self.obj_id = obj_id

    def __str__(self):
        return f"G:{self.group}:{self.obj_type}:{self.obj_id}"


# ****************************************************************************************************************************

# This method is taken from ezomero project :
# https://github.com/TheJacksonLaboratory/ezomero/blob/main/ezomero/_posts.py#L377
def create_dataset(conn, dataset_name, description=None):
    """Create a new dataset.
    Parameters
    ----------
    conn : ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    dataset_name : str
        Name of the new object to be created.
    description : str, optional
        Description for the new Dataset.
    Returns
    -------
    dataset_id : int
        Id of the new Dataset.
    Notes
    -----
    Dataset will be created in the Group specified in the connection. Group can
    be changed using ``conn.SERVICE_OPTS.setOmeroGroup``.
    Examples
    --------
    >>> dataset_id = create_project(conn, "My New Dataset")
    >>> print(dataset_id)
    238
    """
    if type(dataset_name) is not str:
        raise TypeError('Dataset name must be a string')

    if type(description) is not str and description is not None:
        raise TypeError('Dataset description must be a string')

    dataset = DatasetWrapper(conn, model.DatasetI())
    dataset.setName(dataset_name)
    if description is not None:
        dataset.setDescription(description)
    dataset.save()

    return dataset.getId()


def list_images_to_upload(conn, owner, root):
    """List images to upload on OMERO from Deconvolution/omero HRM folder.
    Parameters
    ----------
    conn : ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    owner : str
        Name of the current logged in user
    root : str
        absolute path of HRM-Share folder (from the root mounted on the server)
    Returns
    -------
    image_path_dataset_id_map : dict
        dictionary {image_path:dataset_id} of all images to upload, excluding those that already exists on OMERO.
    path : str
        path that raise an error because the last folder does not exist
    n_initial_images : int  
        Number of images in Deconvolution/omero folder
    """
    image_path_dataset_id_map = {}
    n_initial_images = 0

    if os.path.isdir(root):
        owner_folder = os.path.join(root, owner)
        if not os.path.isdir(owner_folder):
            print("You don't have an active account on HRM. Please go on https://hrm-biop.epfl.ch/ and sign in to HRM")
            print("If you do not have any HRM account, please go on https://hrm-biop.epfl.ch/ and ask for an HRM account")
            return None, owner_folder, -1

        deconvolved_folder = os.path.join(owner_folder, "Deconvolved")
        if not os.path.isdir(deconvolved_folder):
            return None, deconvolved_folder, -1

        omero_folder = os.path.join(deconvolved_folder, "omero")
        if not os.path.isdir(omero_folder):
            return None, omero_folder, -1

        # list projects
        for project_name in os.listdir(omero_folder):
            project_folder = os.path.join(omero_folder, project_name)

            if not os.path.isdir(project_folder):
                return None, project_folder, -1

                # list datasets
            for dataset_name in os.listdir(project_folder):
                dataset_folder = os.path.join(project_folder, dataset_name)

                if not os.path.isdir(dataset_folder):
                    return None, dataset_folder, -1

                # images within a dsataset
                if not dataset_name == "None":
                    d_name_split = dataset_name.split("_")
                    dataset_id = d_name_split[0]

                    dataset = conn.getObject('Dataset', dataset_id)
                    if dataset is not None:
                        for image_name in os.listdir(dataset_folder):
                            # filter only ids images
                            if "ids" in image_name:  # .ids
                                already_existing_image = False
                                n_initial_images += 1
                                dataset_images = dataset.listChildren()

                                # filter image that does not already exist in omero
                                for ex_image in dataset_images:
                                    if ex_image.getName() == image_name:
                                        already_existing_image = True
                                        break

                                if not already_existing_image:
                                    image_path_dataset_id_map[os.path.join(dataset_folder, image_name)] = dataset_id
                # orphaned images
                else:
                    dataset_created = False
                    orphaned_dataset_id = -1
                    for image_name in os.listdir(dataset_folder):
                        # filter only ids images
                        if ".ids" in image_name:  # .ids
                            n_initial_images += 1
                            # create a new for orphaned images 
                            if not dataset_created:
                                orphaned_dataset_id = create_dataset(conn, f"HRM-{date.today()}")
                                dataset_created = True
                            image_path_dataset_id_map[os.path.join(dataset_folder, image_name)] = orphaned_dataset_id

        return image_path_dataset_id_map, None, n_initial_images
    else:
        return None, root, -1


def delete_uploaded_files(image_path):
    """Delete image in the deconvolved folder
    ----------
    image_path : str
        Path to image to delete.
    Returns
    -------
    bool
        True in case of success, False otherwise.
    """
    # file name with extension
    image_name = os.path.basename(image_path)

    # file name without extension
    image_name_without_ext = os.path.splitext(image_name)[0]

    # parent file
    parent_folder = os.path.abspath(os.path.join(image_path, os.pardir))

    for path in os.listdir(parent_folder):
        # check if current path is a file
        file = os.path.join(parent_folder, path)
        if os.path.isfile(file) and (image_name_without_ext in file):
            print("INFO", f"Delete file [{file}]")
            os.remove(file)

    if len(os.listdir(parent_folder)) == 0:
        parent_parent_folder = os.path.abspath(os.path.join(parent_folder, os.pardir))
        print("INFO", f"Delete parent directory [{parent_folder}]")
        os.rmdir(parent_folder)
        if len(os.listdir(parent_parent_folder)) == 0:
            print("INFO", f"Delete parent directory [{parent_parent_folder}]")
            os.rmdir(parent_parent_folder)


def delete_raw_files(image_path):
    """Delete image
    ----------
    image_path : str
        Path to image to delete.
    Returns
    -------
    bool
        True in case of success, False otherwise.
    """
    # file name with extension
    image_name = os.path.basename(image_path)

    # file name without extension
    raw_image_name_without_ext = parse_image_basename(image_name)

    # parent file
    parent_folder = os.path.abspath(os.path.join(image_path, os.pardir))
    parent_folder = parent_folder.replace("Deconvolved", "Raw")

    if os.path.exists(parent_folder):

        for path in os.listdir(parent_folder):
            # check if current path is a file
            file = os.path.join(parent_folder, path)
            if os.path.isfile(file) and (raw_image_name_without_ext in file):
                print("INFO", f"Delete file [{file}]")
                os.remove(file)

        if len(os.listdir(parent_folder)) == 0:
            parent_parent_folder = os.path.abspath(os.path.join(parent_folder, os.pardir))
            print("INFO", f"Delete parent directory [{parent_folder}]")
            os.rmdir(parent_folder)
            if len(os.listdir(parent_parent_folder)) == 0:
                print("INFO", f"Delete parent directory [{parent_parent_folder}]")
                os.rmdir(parent_parent_folder)
    else:
        print("WARN", f"The path{parent_folder} does not exist ; raw images are not deleted")


def upload_images_from_hrm(conn, script_params):
    """Upload images from HRM-SHare folder
    Parameters
    ----------
    conn : ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    script_params : dict
        User defined parameters
    Returns
    -------
    message : str
        Informative message for the user.
    """

    # OMERO host
    host = script_params[SERVER_PARAM_NAME]
    # OMERO port
    port = script_params[PORT_PARAM_NAME]
    # remove existing images
    delete_uploaded_images = script_params[DELETE_DECONVOLVED_PARAM_NAME]
    # remove existing images
    delete_raw_images = script_params[DELETE_RAW_PARAM_NAME]

    # root path to HRM-Share folder
    root = "/mnt/hrmshare"
    # current logged in user
    owner = conn.getUser().getOmeName()
    # current group ID
    group_id = conn.getGroupFromContext().getId()
    # list of images to upload
    image_path_dataset_id_map, failed_path, n_initial_images = list_images_to_upload(conn, owner, root)

    if image_path_dataset_id_map is not None:
        # open the connection
        cli = CLI()
        cli.register('import', ImportControl, '_')
        cli.register('sessions', SessionsControl, '_')

        total_images = len(image_path_dataset_id_map)
        total_images_uploaded = 0
        total_kvps_uploaded = 0
        total_tags_uploaded = 0
        total_files_uploaded = 0
        try:
            for image in image_path_dataset_id_map.items():
                # built the object ID
                dataset_id = image[1]
                image_path = image[0]
                dataset_id_obj = OmeroId(f"G:{group_id}:Dataset:{dataset_id}")

                # upload image on omero
                image_id_obj = to_omero(conn, cli, host, port, dataset_id_obj, image_path)
                total_images_uploaded += (1 if image_id_obj is not None else 0)
                has_failed = False

                # add deconvolution parameters as key-value pairs
                try:
                    summary = parse_summary(image_path)
                    total_kvps_uploaded += (1 if add_annotation_key_value(conn, image_id_obj, summary) else 0)
                except Exception as err:  # pragma: no cover # pylint: disable-msg=broad-except
                    print("ERROR", f"Fail creating a parameter summary from [{image_path}] : {err}")
                    has_failed = True

                # transfer tag from raw to deconvolved image
                try:
                    total_tags_uploaded += (1 if add_tags(conn, image_id_obj, dataset_id_obj) else 0)
                except Exception as err:
                    print("ERROR", f"Fail adding tags from raw image to image [{image_id_obj}] : {err}")
                    has_failed = True

                # attach the log file to the image
                try:
                    total_files_uploaded += (1 if attach_log_file(conn, image_id_obj, image_path) else 0)
                except Exception as err:
                    print("ERROR", f"Fail attaching log file from [{image_path}] to image {image_id_obj.obj_id} : {err}")
                    has_failed = True

                if image_id_obj is not None and not has_failed:
                    if delete_uploaded_images:
                        delete_uploaded_files(image_path)
                    if delete_raw_images:
                        delete_raw_files(image_path)
        finally:
            cli.close()

        n_existing_images = n_initial_images - total_images
        message = f"{total_images_uploaded} / {n_initial_images} images uploaded and" \
                  f" {n_existing_images} / {n_initial_images} images already existing --  " \
                  f"{total_kvps_uploaded} / {n_initial_images} images have KVP added -- " \
                  f"{total_tags_uploaded} / {n_initial_images} images have tags transferred -- " \
                  f"{total_files_uploaded} / {n_initial_images} images have files added"

    else:
        if n_initial_images == 0:
            message = f"There is no image to upload"
        else:
            message = f"The path {failed_path} is not valid. Cannot upload any images."

    return message


def run_script():
    client = scripts.client(
        'Retrieve images from HRM-Share folder',
        """
    This script retrieves .ids images from your HRM folder (\\sv-nas1.rcp.epfl.ch\ptbiop\public\HRM-Share\Deconvolved\omero) and uploads them on OMERO. It also parses the .parameter.txt file to add all parameters as key-values. Please clean this folder to be sure that it only contains images you want to upload.
        """,
        scripts.String(
            SERVER_PARAM_NAME, optional=False, grouping="1",
            description="OMERO server address", default="omero-server.epfl.ch"),

        scripts.Int(
            PORT_PARAM_NAME, optional=False, grouping="2",
            description="OMERO port", default=4064),

        scripts.Bool(
            DELETE_DECONVOLVED_PARAM_NAME, optional=True, grouping="3",
            description="Remove uploaded images from HRM folder", default=False),

        scripts.Bool(
            DELETE_RAW_PARAM_NAME, optional=True, grouping="4",
            description="Remove corresponding raw images from HRM folder", default=False),

        authors=["Rémy Dornier"],
        institutions=["EPFL - BIOP"],
        contact="omero@groupes.epfl.ch"
    )

    try:
        # process the list of args above.
        script_params = {}
        for key in client.getInputKeys():
            if client.getInput(key):
                script_params[key] = client.getInput(key, unwrap=True)

        # wrap client to use the Blitz Gateway
        conn = BlitzGateway(client_obj=client)
        print("script params")
        for k, v in script_params.items():
            print(k, v)
        message = upload_images_from_hrm(conn, script_params)
        client.setOutput("Message", rstring(message))

    finally:
        client.closeSession()


if __name__ == "__main__":
    run_script()
