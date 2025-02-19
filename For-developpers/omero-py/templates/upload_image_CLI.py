
"""
Upload a list of local images into a specific dataset in OMERO, using the CLI

Code copied and adapted from :
- https://github.com/imcf/hrm-omero/blob/main/src/hrm_omero/transfer.py

Thanks to Niko Ehrenfeuchter for his job
"""

import omero
from omero.gateway import BlitzGateway
import os
import tempfile
import yaml
from omero.cli import CLI
import traceback


def upload_on_omero(conn, dataset_id, image_file, omero_logfile="", _fetch_zip_only=False):
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
    dataset_id : int
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
    bool
        True in case of success, False otherwise.

    Raises
    ------
    TypeError
        Raised in case `image_file` is in a format that is not supported by OMERO.

    """
    # TODO: revisit this, as e.g. BDV .h5 files are supported for now!
    if image_file.lower().endswith((".h5", ".hdf5")):
        msg = f"ERROR importing [{image_file}]: HDF5 format not supported by OMERO!"
        print("ERROR", msg)
        raise TypeError(msg)

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

    # Currently there is no direct "Python way" to import data into OMERO, so we have to
    # use the CLI wrapper for this...
    # TODO: check the more recent code mentioned by the OME developers in the forum
    # thread: https://forum.image.sc/t/automated-uploader-to-omero-in-python/38290
    # https://gitlab.com/openmicroscopy/incubator/omero-python-importer/-/blob/master/import.py)
    # and also see https://pypi.org/project/omero-upload/

    cli = CLI()
    cli.loadplugins()
    cli.set_client(conn.c)
    import_args = ["import"]

    # disable upgrade checks (https://forum.image.sc/t/unable-to-use-cli-importer/26424)
    # import_args.extend(["--skip", "upgrade"])

    if omero_logfile:
        print(f"Messages (stderr) from import will go to [{omero_logfile}].")
        import_args.extend(["--debug", "ALL"])
        import_args.extend(["--errs", omero_logfile])

    import_args.extend(["-d", str(dataset_id)])

    # capture stdout and request YAML format to parse the output later on:
    tempdir = tempfile.TemporaryDirectory(prefix="hrm-omero__")
    cap_stdout = f"{tempdir.name}/omero-import-stdout"
    print(f"Capturing stdout of the 'omero' call into [{cap_stdout}]...")
    import_args.extend(["--file", cap_stdout])
    import_args.extend(["--output", "yaml"])

    #### for ann_id in annotations:
    ####     import_args.extend(['--annotation_link', str(ann_id)])
    import_args.append(image_file)
    if _fetch_zip_only:
        # calling 'import --advanced-help' will trigger the download of OMERO.java.zip
        # in case it is not yet present (the extract_image_id() call will then fail,
        # resulting in the whole function returning "False")
        print("WARNING", "As '_fetch_zip_only' is set NO IMPORT WILL BE ATTEMPTED!")
        import_args = ["import", "--advanced-help"]
    print(f"import_args: {import_args}")
    try:
        cli.invoke(import_args, strict=True)
        imported_id = extract_image_id(cap_stdout)
        print(f"Imported OMERO image ID: {imported_id}")
    except PermissionError as err:
        print("ERROR", err)
        omero_userdir = os.environ.get("OMERO_USERDIR", "<not-set>")
        print("ERROR", f"Current OMERO_USERDIR value: {omero_userdir}")
        print(
            "ERROR",
            (
                "Please make sure to read the documentation about the 'OMERO_USERDIR' "
                "environment variable and also check if the file to be imported has "
                "appropriate permissions!"
            ),
        )
        return False
    except Exception as err:  # pylint: disable-msg=broad-except
        print("ERROR", f"ERROR: uploading '{image_file}' to dataset {dataset_id} failed!")
        print("ERROR", f"OMERO error message: >>>{err}<<<")
        print("WARNING", f"import_args: {import_args}")
        return False
    finally:
        tempdir.cleanup()


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
            if parsed[0]["Fileset"] is not None:
                image_id = ",".join([str(img_id) for img_id in parsed[0]["Image"]])
            else:
                msg = f"Unexpected YAML retrieved from OMERO, unable to parse:\n{parsed}"
                print("ERROR", msg)
                raise SyntaxError(msg)
        else:
            image_id = parsed[0]["Image"][0]
    except Exception as err:  # pylint: disable-msg=broad-except
        print("ERROR", f"Error parsing imported image ID from YAML output: {err}")
        return None

    print(f"Successfully parsed Image ID from YAML: {image_id}")
    return image_id


def main():
    dataset_id = 1
    paths = ["path/to/image1", "path/to/image2"]

    host = "localhost"
    conn = BlitzGateway("username", "password", host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            dataset = conn.getObject('Dataset', dataset_id)
            if not dataset:
                print('Dataset id not found: %s' % dataset)

            else:
                for fs_path in paths:
                    print('Importing: %s' % fs_path)
                    upload_on_omero(conn, dataset_id, fs_path)

        except Exception as e:
            print(e)
            traceback.print_exc()

        finally:
            conn.close()
            print(f"Disconnect from {host}")


if __name__ == '__main__':
    main()
