```
This script can be used to delete all annotations that are orphaned on the database.

Author & copyright: Emily Yunha Shin @eyshin05 - 2024
URL : https://forum.image.sc/t/how-to-handle-orphaned-annotations-in-omero/96883/3
```

import omero
from omero.rtypes import rstring

client = omero.client(OMERO_URL, OMERO_PORT)
client.createSession(OMERO_ID, OEMRO_PASS)

conn = BlitzGateway(client_obj=client)

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

client.closeSession()