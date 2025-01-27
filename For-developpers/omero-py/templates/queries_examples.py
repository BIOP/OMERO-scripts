from omero.gateway import BlitzGateway
import omero
from omero.rtypes import wrap


def run_script():
    conn = BlitzGateway("username", "password", host="localhost", port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        try:
            '''
            Scenario 1
            -- hard-coded everything
            -- working in the default group only
            '''
            params = omero.sys.ParametersI()
            qs = conn.getQueryService()
            q = "select shape.id from Shape shape where shape.textValue = 'test'"

            results = qs.projection(q, params, conn.SERVICE_OPTS)
            [print(result[0].val) for result in results]


            '''
            Scenario 2
            -- with where_clause & params
            -- working in the default group only
            '''
            where_clause = []

            params.add('filter', wrap([f"'Figure_{201}%'"]))
            where_clause.append(f"mv.name like 'Figure_{205}%'")

            params.add('ns', wrap(["omero.web.figure.link"]))
            where_clause.append(f"a.ns in (:ns)")
            where_clause.append("mv.value != '' ")

            qs = conn.getQueryService()
            q = """
                        select distinct a.id, mv.name
                            from Annotation a
                            join a.mapValue mv where %s
                        """ % (" and ".join(where_clause))

            results = qs.projection(q, params, conn.SERVICE_OPTS)
            [print(result[0].val) for result in results]
            [print(result[1].val) for result in results]


            '''
            Scenario 3
            -- to work on multiple groups
            '''
            # Retrieve the services we are going to use
            admin_service = conn.getAdminService()

            ec = admin_service.getEventContext()
            groups = [admin_service.getGroup(v) for v in ec.memberOfGroups]
            for group in groups:
                print('Searching in group: %s' % group.name.val)
                conn.SERVICE_OPTS.setOmeroGroup(group.id.val)

                params = omero.sys.ParametersI()
                qs = conn.getQueryService()
                q = "select shape from Shape shape where shape.textValue = 'test2'"

                results = qs.findAllByQuery(q, params, conn.SERVICE_OPTS)
                [print(result) for result in results]

        finally:
            conn.close()


if __name__ == "__main__":
    run_script()

"""
3 getters (see https://forum.image.sc/t/sql-query-failed/100712/3)
- qs.findAllByQuery(query, omero.sys.ParametersI(), conn.SERVICE_OPTS)
- qs.findByQuery(query, omero.sys.ParametersI(), conn.SERVICE_OPTS)
- qs.projection(query, omero.sys.ParametersI(), conn.SERVICE_OPTS)
"""

"""
Other query examples

- f"select roi.id from Roi as roi where roi.image = {object_id}"
- "select distinct map.value from Annotation ann join ann.mapValue map where map.name = 'ArgoSlide_name'"

# Complex query with sub queries
- "select a from Annotation a where a.id in (select link.child from AnnotationAnnotationLink link " \
  "where link.parent in (select ann.id from Annotation ann where ann.ns='openmicroscopy.org/omero/insight/tagset' " \
  "and ann.textValue='protein-atlas-set'))"


# Complex query using 'distinct', 'group by' & 'having' keywords
-   query_parts = ["ImageAnnotationLink link"]
    conditions = ["link.child=" + str(common_tag_id)]

    for i, tag_id in enumerate(self.tag_list):
        query_parts.append("ImageAnnotationLink link%s" % (i + 1))
        conditions.append("link.parent = link%s.parent" % (i + 1))
        conditions.append("link%s.child=%s" % (i + 1, tag_id))

    final_part = ""
    if is_exclusive:
        final_part = " and p.id in (%s) group by c.id having count(c) = %s" % (
            ",".join([str(dst_id) for dst_id in self.dataset_list]), str(len(self.tag_list)))
    
    intermediate_query = "select link.parent from %s where %s" % (", ".join(query_parts), " and ".join(conditions))
    
    "select distinct c.id from DatasetImageLink dil join dil.child c join dil.parent p where dil.child in (%s)%s" % (intermediate_query, final_part)



"""