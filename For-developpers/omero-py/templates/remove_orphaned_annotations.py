"""
This script can be used to delete all annotations that are orphaned on the database.

Author & copyright: Emily Yunha Shin @eyshin05 - 2024
URL : https://forum.image.sc/t/how-to-handle-orphaned-annotations-in-omero/96883/3
"""
from omero.gateway import BlitzGateway
import traceback


def run_script():
    host = "localhost"
    conn = BlitzGateway("username", "password", host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            # search in all the user's group
            conn.SERVICE_OPTS.setOmeroGroup('-1')

            query = """
                select a from Annotation a
                where a.ns is Null
                and a.id not in (select l.child.id from ProjectAnnotationLink l)
                and a.id not in (select l.child.id from DatasetAnnotationLink l)
                and a.id not in (select l.child.id from ImageAnnotationLink l)
                and a.id not in (select l.child.id from ScreenAnnotationLink l)
                and a.id not in (select l.child.id from PlateAnnotationLink l)
                and a.id not in (select l.child.id from WellAnnotationLink l)
            """

            svc = conn.getQueryService()
            annotations = svc.findAllByQuery(query, None)

            ids_to_delete = [ann.id.val for ann in annotations]

            if ids_to_delete:
                conn.deleteObjects('Annotation', ids_to_delete)

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")


if __name__ == "__main__":
    run_script()
