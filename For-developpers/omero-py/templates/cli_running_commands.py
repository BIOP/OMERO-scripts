from omero.gateway import BlitzGateway
from omero.cli import cli_login
from omero.plugins.sessions import SessionsControl
from omero.plugins.duplicate import DuplicateControl


# Example : running command from the omero-cli-duplicate plugin
# https://github.com/ome/omero-cli-duplicate
def run_script():
    with cli_login("-u", "username", "-w", "password", "-s", "localhost", "-p", "port") as cli:
        # connect to OMERO server
        conn = BlitzGateway(client_obj=cli.get_client())

        if conn.isConnected():
            try:
                cli.register('duplicate', DuplicateControl, '_')
                cli.register('sessions', SessionsControl, '_')

                image_ids = [1, 2]
                str_ids = [str(img_id) for img_id in image_ids]
                import_args = ["duplicate", f"Image:{','.join(str_ids)}"]

                try:
                    cli.invoke(import_args, strict=True)
                    print("SUCCESS", f"Imported OMERO image ID:")
                except PermissionError as err:
                    print(f"Error during duplication {err}")

                cli.get_client().closeSession()
            finally:
                conn.close()


if __name__ == "__main__":
    run_script()
