import omero.scripts as scripts
from omero.rtypes import rlong, rstring
from omero.gateway import BlitzGateway
from omero.cli import cli_login
from omero.plugins.sessions import SessionsControl
from omero.plugins.duplicate import DuplicateControl


def main_loop(conn, script_params):
    """
    Do your stuff that do not require the cli here
    """

    # open cli connection
    with cli_login("-k", "%s" % conn.c.getSessionId(), "-s", "localhost", "-p", "port") as cli:
        cli.register('duplicate', DuplicateControl, '_')
        cli.register('sessions', SessionsControl, '_')

        image_ids = [1, 2]
        str_ids = [str(img_id) for img_id in image_ids]
        import_args = ["duplicate", f"Image:{','.join(str_ids)}"]

        # launch duplication
        try:
            cli.invoke(import_args, strict=True)
            print("SUCCESS", f"Duplicated images {str_ids}")
        except PermissionError as err:
            print(f"Error during duplication of images {str_ids}: {err}")

        cli.get_client().closeSession()

    """
    Do your stuff that do not require the cli here
    If you need to invoke another command with the cli, 
    you need to open the connection again, even if you do not 
    close the session.
    """

    # open cli connection
    with cli_login("-k", "%s" % conn.c.getSessionId(), "-s", "localhost", "-p", "port") as cli:
        cli.register('duplicate', DuplicateControl, '_')
        cli.register('sessions', SessionsControl, '_')

        image_ids = [1, 2]
        str_ids = [str(img_id) for img_id in image_ids]
        import_args = ["duplicate", f"Image:{','.join(str_ids)}"]

        # launch duplication
        try:
            cli.invoke(import_args, strict=True)
            print("SUCCESS", f"Duplicated images {str_ids}")
        except PermissionError as err:
            print(f"Error during duplication of images {str_ids}: {err}")

        cli.get_client().closeSession()

    return ""


def run_script():
    client = scripts.client(
        'Short name of the script',
        """
    Description of the script
        """,
        scripts.String(
            "data_type", optional=False, grouping="1",
            description="Choose source of objects",
            values=[rstring("Image"), rstring("Dataset")], default="Image"),

        scripts.List(
            "ids",  optional=False, grouping="2",
            description="List of Images IDs to link to another"
                        " group.").ofType(rlong(0)),

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
        message = main_loop(conn, script_params)
        client.setOutput("Message", rstring(message))
    finally:
        client.closeSession()


if __name__ == "__main__":
    run_script()
