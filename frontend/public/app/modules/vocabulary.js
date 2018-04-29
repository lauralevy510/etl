((definition) => {
    if (typeof define === "function" && define.amd) {
        define([], definition);
    }
})(() => {
    "use strict";

    // TODO Split into LP and SKOS vocabulary, so it can be used right after import.

    const ETL_LP = "http://etl.linkedpipes.com/ontology/";
    const LP = "http://linkedpipes.com/ontology/";

    const LP_VOCAB = {
        "DU_SESAME_CHUNKED": LP + "dataUnit/sesame/1.0/rdf/Chunked",
        "DU_SESAME_SINGLE_GRAPH": LP + "dataUnit/sesame/1.0/rdf/SingleGraph",
        "HAS_PIPELINE": ETL_LP + "pipeline",
        "HAS_STATUS": ETL_LP + "status",
        "HAS_DELETE_WORKING_DATA": LP + "deleteWorkingData",
        "EXEC_FINISHED": "http://etl.linkedpipes.com/resources/status/finished",
        "EXEC_FAILED" : "http://etl.linkedpipes.com/resources/status/failed",
        "EXEC_CANCELLED": "http://etl.linkedpipes.com/resources/status/cancelled",
        "EXEC_MAPPED": "http://etl.linkedpipes.com/resources/status/mapped",
        "HAS_EVENT_CREATED": LP + "events/created",
        "HAS_COMPONENT" : LP + "component",
        "HAS_EVENT_REASON" : LP + "events/reason",
        "HAS_EVENT_ROOT_CAUSE": LP + "events/rootException",
        "HAS_EXEC_ORDER": LP + "executionOrder",
        "HAS_DU" : ETL_LP + "dataUnit",
        "HAS_BINDING": ETL_LP + "binding",
        "HAS_DEBUG" : ETL_LP + "debug",
        "PROGRESS_TOTAL" : LP + "progress/total",
        "PROGRESS_CURRENT" : LP + "progress/current",
        "HAS_ORDER": LP + "order",
        "EXECUTION": ETL_LP + "Execution",
        "EXECUTION_BEGIN": LP + "events/ExecutionBegin",
        "EXECUTION_END": LP + "events/ExecutionEnd",
        "CMP_BEGIN": LP + "events/ComponentBegin",
        "CMP_END" : LP + "events/ComponentEnd",
        "CMP_FAILED" : LP + "events/ComponentFailed",
        "COMPONENT": LP + "Component",
        "TEMPLATE" : LP + "Template",
        "HAS_TEMPLATE" : LP + "template",
        "DATA_UNIT": ETL_LP + "DataUnit",
        "PROGRESS_REPORT": LP + "progress/ProgressReport",
        "EXEC_OPTIONS" : ETL_LP + "ExecutionOptions",
        "SAVE_DEBUG" : LP + "saveDebugData",
        "DELETE_WORKING": LP + "deleteWorkingData",
        "HAS_TAG": ETL_LP + "tag",
        "PIPELINE": LP + "Pipeline",
        // TODO Merge TOMBSTONE and DELETED
        "TOMBSTONE" : LP + "Tombstone",
        "DELETED": ETL_LP + "Deleted",
        "HAS_REPO_POLICY": LP + "rdfRepositoryPolicy",
        "HAS_REPO_TYPE": LP + "rdfRepositoryType",
        "UPDATE_OPTIONS" : LP + "UpdateOptions",
        "HAS_IMPORT_TEMPLATES": LP +  "importTemplates",
        "HAS_UPDATE_TEMPLATES": LP + "updateTemplates"

    };

    const SKOS_VOCAB = {
        "PREF_LABEL": "http://www.w3.org/2004/02/skos/core#prefLabel"
    };

    return {
        "LP" : LP_VOCAB,
        "SKOS": SKOS_VOCAB
    };

});
