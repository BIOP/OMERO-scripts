from omero.gateway import BlitzGateway
import traceback


def run_script():
    object_type = "Image"
    object_id = 303
    host = "localhost"

    conn = BlitzGateway("username", "password", host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            # search in all the user's group
            conn.SERVICE_OPTS.setOmeroGroup('-1')

            omero_object = conn.getObject(object_type, object_id)
            print_parent(omero_object, object_type)

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnect from {host}")


def print_parent(omero_object, object_type):
    if object_type == 'Image' or \
            object_type == 'Dataset' or \
            object_type == 'Well' or \
            object_type == 'Plate':
        print(f'{object_type} : {omero_object.getName()} | {omero_object.getId()}')
        parent = omero_object.getParent()
        print_parent(parent, parent.OMERO_CLASS)
    if object_type == 'Screen' or object_type == 'Project':
        print(f'{object_type} : {omero_object.getName()} | {omero_object.getId()}')


if __name__ == "__main__":
    run_script()
