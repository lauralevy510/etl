package com.linkedpipes.etl.storage.pipeline;

import com.linkedpipes.etl.storage.Configuration;
import com.linkedpipes.etl.storage.pipeline.importer.ImportFacade;
import com.linkedpipes.etl.storage.pipeline.migration.MigrationFacade;
import com.linkedpipes.etl.storage.pipeline.updater.UpdaterFacade;
import com.linkedpipes.etl.storage.rdf.PojoLoader;
import com.linkedpipes.etl.storage.rdf.RdfUtils;
import org.openrdf.model.IRI;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.SimpleValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.SKOS;
import org.openrdf.rio.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.linkedpipes.etl.storage.rdf.RdfUtils.write;

/**
 * Manage pipeline storage.
 *
 * @author Petr Škoda
 */
@Service
class PipelineManager {

    private static final Logger LOG
            = LoggerFactory.getLogger(PipelineManager.class);

    private static final SimpleDateFormat DATE_FORMAT
            = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private Configuration configuration;

    @Autowired
    private ImportFacade importFacade;

    @Autowired
    private MigrationFacade migrationFacade;

    @Autowired
    private UpdaterFacade updaterFacade;

    /**
     * Store pipelines.
     */
    private final Map<String, Pipeline> pipelines = new HashMap<>();

    /**
     * Contains list of used or reserved IRIs.
     */
    private final Set<String> reserved = new HashSet<>();

    /**
     * Object use as a lock, for inner synchronisation.
     */
    private final Object lock = new Object();

    @PostConstruct
    public void initialize() {
        final File pipelineDirectory = configuration.getPipelinesDirectory();
        if (!pipelineDirectory.exists()) {
            pipelineDirectory.mkdirs();
        }
        for (File file : pipelineDirectory.listFiles()) {
            // Read only files without the .backup extension.
            final String fileName = file.getName().toLowerCase();
            if (!file.isFile() || fileName.endsWith(".backup")) {
                continue;
            }
            // Load pipeline.
            try {
                final Pipeline pipeline = loadPipeline(file);
                pipelines.put(pipeline.getIri(), pipeline);
                reserved.add(pipeline.getIri());
            } catch (Exception ex) {
                LOG.error("Can't read pipeline: {}", file, ex);
            }
        }
    }

    /**
     * Load pipeline from a given file. Check version and perform migrationFacade
     * if necessary.
     *
     * @param file
     * @return Loaded pipeline.
     */
    protected Pipeline loadPipeline(File file)
            throws PipelineFacade.OperationFailed {
        Collection<Statement> pipelineRdf;
        Pipeline.Info info;
        try {
            pipelineRdf = RdfUtils.read(file);
            info = new Pipeline.Info();
            PojoLoader.loadOfType(pipelineRdf, Pipeline.TYPE, info);
        } catch (PojoLoader.CantLoadException | RdfUtils.RdfException ex) {
            throw new PipelineFacade.OperationFailed("Can't read pipeline: {}",
                    file, ex);
        }
        // Migration.
        if (info.getVersion() != Pipeline.VERSION_NUMBER) {
            // Create backup file.
            String fileName = file.getName();
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
            final File backupFile = new File(file.getParent(), fileName +
                    "_" + DATE_FORMAT.format(new Date()) + ".trig.backup");
            try {
                RdfUtils.write(backupFile, RDFFormat.TRIG, pipelineRdf);
            } catch (RdfUtils.RdfException ex) {
                throw new PipelineFacade.OperationFailed(
                        "Can't write backup file: {}", backupFile, ex);
            }
            // Perform migrationFacade of the pipeline definition.
            try {
                pipelineRdf = migrationFacade.migrate(pipelineRdf);
                info = new Pipeline.Info();
                PojoLoader.loadOfType(pipelineRdf, Pipeline.TYPE, info);
            } catch (MigrationFacade.MigrationFailed |
                    PojoLoader.CantLoadException ex) {
                throw new PipelineFacade.OperationFailed(
                        "Can't migrate pipeline: {}", file, ex);
            }
            // We need to create backup of the pipeline file
            // and write updated pipeline to new file.
            final File newFile = new File(file.getParent(), fileName + ".trig");
            // Write new file.
            try {
                RdfUtils.write(newFile, RDFFormat.TRIG, pipelineRdf);
            } catch (RdfUtils.RdfException ex) {
                throw new PipelineFacade.OperationFailed(
                        "Can't write new pipeline to file: {}", newFile, ex);
            }
            // Delete old file and switch to new file.
            file.delete();
            file = newFile;
        }
        // Create pipeline record.
        final Pipeline pipeline = new Pipeline(file, info);
        createPipelineReference(pipeline);
        return pipeline;
    }

    /**
     * Create and set reference for the pipeline. The pipeline reference
     * consists from typed pipeline resource and labels. Use information
     * in the pipeline.info.
     *
     * @param pipeline
     */
    protected static void createPipelineReference(Pipeline pipeline) {
        final ValueFactory vf = SimpleValueFactory.getInstance();
        final IRI pipelineIri = vf.createIRI(pipeline.getIri());
        final List<Statement> referenceRdf = new ArrayList<>(4);
        //
        referenceRdf.add(vf.createStatement(pipelineIri,
                RDF.TYPE, Pipeline.TYPE, pipelineIri));
        for (Value label : pipeline.getInfo().getLabels()) {
            referenceRdf.add(vf.createStatement(pipelineIri, SKOS.PREF_LABEL,
                    label, pipelineIri));
        }
        //
        pipeline.setReferenceRdf(referenceRdf);
    }

    /**
     * @return A map of all pipelines.
     */
    public Map<String, Pipeline> getPipelines() {
        return Collections.unmodifiableMap(pipelines);
    }

    /**
     * @return Reserved pipeline IRI as string.
     */
    public String reservePipelineIri() {
        String iri;
        synchronized (lock) {
            do {
                iri = configuration.getDomainName() +
                        "/resources/pipelines/created-" +
                        (new Date()).getTime();
            } while (reserved.contains(iri));
            reserved.add(iri);
        }
        return iri;
    }

    /**
     * @param iri
     * @return Empty pipeline of given IRI.
     */
    private final Collection<Statement> createEmptyPipeline(IRI iri) {
        final ValueFactory vf = SimpleValueFactory.getInstance();
        return Arrays.asList(
                vf.createStatement(iri, RDF.TYPE, Pipeline.TYPE, iri),
                vf.createStatement(iri, Pipeline.HAS_VERSION,
                        vf.createLiteral(Pipeline.VERSION_NUMBER), iri),
                vf.createStatement(iri, SKOS.PREF_LABEL,
                        vf.createLiteral(iri.stringValue()), iri)
        );
    }

    /**
     * Import pipeline from given data and apply given options.
     *
     * @param pipelineRdf
     * @param optionsRdf
     * @return
     */
    public Pipeline createPipeline(Collection<Statement> pipelineRdf,
            Collection<Statement> optionsRdf)
            throws PipelineFacade.OperationFailed {
        final PipelineOptions options = new PipelineOptions();
        try {
            PojoLoader.loadOfType(optionsRdf, PipelineOptions.TYPE, options);
        } catch (PojoLoader.CantLoadException ex) {
            throw new PipelineFacade.OperationFailed("Can't load options.", ex);
        }
        // Get pipeline IRI.
        if (options.getPipelineIri() == null) {
            final IRI iri = SimpleValueFactory.getInstance().createIRI(
                    reservePipelineIri());
            options.setPipelineIri(iri);
        }
        // If we do not have pipeline take and empty one.
        if (pipelineRdf.isEmpty()) {
            pipelineRdf = createEmptyPipeline(options.getPipelineIri());
        }
        // Perform import if needed.
        if (!options.isLocal()) {
            pipelineRdf = importFacade.update(pipelineRdf, options);
        }
        // Read pipeline info.
        Pipeline.Info info = new Pipeline.Info();
        try {
            PojoLoader.loadOfType(pipelineRdf, Pipeline.TYPE, info);
        } catch (PojoLoader.CantLoadException ex) {
            throw new PipelineFacade.OperationFailed(
                    "Can't read pipeline.", ex);
        }
        // Check version.
        if (info.getVersion() != Pipeline.VERSION_NUMBER) {
            try {
                pipelineRdf = migrationFacade.migrate(pipelineRdf);
            } catch (MigrationFacade.MigrationFailed ex) {
                throw new PipelineFacade.OperationFailed(
                        "Migration failed from version: {}",
                        info.getVersion(), ex);
            }
        }
        // Perform updates.
        try {
            pipelineRdf = updaterFacade.update(pipelineRdf,
                    options.getPipelineIri(), options);
        } catch (UpdaterFacade.UpdateFailed ex) {
            throw new PipelineFacade.OperationFailed(
                    "Can't perform required updates.", ex);
        }
        // Reload info from current version.
        // TODO This is not necessary if there is no change.
        info = new Pipeline.Info();
        try {
            PojoLoader.loadOfType(pipelineRdf, Pipeline.TYPE, info);
        } catch (PojoLoader.CantLoadException ex) {
            throw new PipelineFacade.OperationFailed(
                    "Can't read pipeline.", ex);
        }
        // Create pipeline.
        final String fileName
                = options.getPipelineIri().getLocalName() + ".trig";
        final Pipeline pipeline = new Pipeline(
                new File(configuration.getPipelinesDirectory(), fileName),
                info);
        createPipelineReference(pipeline);
        // TODO Use event to notify about changes !
        try {
            RdfUtils.write(pipeline.getFile(), RDFFormat.TRIG, pipelineRdf);
        } catch (RdfUtils.RdfException ex) {
            pipeline.getFile().delete();
            //
            throw new PipelineFacade.OperationFailed(
                    "Can't write pipeline to {}", pipeline.getFile(), ex);
        }
        pipelines.put(options.getPipelineIri().stringValue(), pipeline);
        return pipeline;
    }

    /**
     * Update pipeline definition and perform all related operations.
     *
     * @param pipeline
     * @param pipelineRdf
     */
    public void updatePipeline(Pipeline pipeline,
            Collection<Statement> pipelineRdf)
            throws PipelineFacade.OperationFailed {
        // Update pipeline in-memory model.
        Pipeline.Info info = new Pipeline.Info();
        try {
            PojoLoader.loadOfType(pipelineRdf, Pipeline.TYPE, info);
            pipeline.setInfo(info);
            //
            createPipelineReference(pipeline);
        } catch (PojoLoader.CantLoadException ex) {
            throw new PipelineFacade.OperationFailed(
                    "Can't read pipeline.", ex);
        }
        // Write to disk.
        try {
            write(pipeline.getFile(), RDFFormat.TRIG, pipelineRdf);
        } catch (RdfUtils.RdfException ex) {
            throw new PipelineFacade.OperationFailed(
                    "Can't write pipeline: {}", pipeline.getFile(), ex);
        }
        // TODO Use events to notify all about pipeline change !
    }

    /**
     * Delete given pipeline.
     *
     * @param pipeline
     */
    public void deletePipeline(Pipeline pipeline) {
        // TODO Add tomb-stone
        pipeline.getFile().delete();
        pipelines.remove(pipeline.getIri());
        // TODO Use event to notify about changes !
    }

    public Collection<Statement> localizePipeline(
            Collection<Statement> pipelineRdf, Collection<Statement> optionsRdf)
            throws PipelineFacade.OperationFailed {
        if (pipelineRdf.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        //
        final PipelineOptions options = new PipelineOptions();
        try {
            PojoLoader.loadOfType(optionsRdf, PipelineOptions.TYPE, options);
        } catch (PojoLoader.CantLoadException ex) {
            throw new PipelineFacade.OperationFailed("Can't load options.", ex);
        }
        // Load pipeline info.
        Pipeline.Info info = new Pipeline.Info();
        try {
            PojoLoader.loadOfType(pipelineRdf, Pipeline.TYPE, info);
        } catch (PojoLoader.CantLoadException ex) {
            throw new PipelineFacade.OperationFailed(
                    "Can't read pipeline.", ex);
        }
        // Perform import if needed.
        if (!options.isLocal()) {
            pipelineRdf = importFacade.update(pipelineRdf, options);
        }
        // Check version.
        if (info.getVersion() != Pipeline.VERSION_NUMBER) {
            try {
                pipelineRdf = migrationFacade.migrate(pipelineRdf);
            } catch (MigrationFacade.MigrationFailed ex) {
                throw new PipelineFacade.OperationFailed(
                        "Migration failed from version: {}",
                        info.getVersion(), ex);
            }
        }
        // Get pipeline IRI.
        if (options.getPipelineIri() == null) {
            final IRI iri = SimpleValueFactory.getInstance().createIRI(
                    reservePipelineIri());
            options.setPipelineIri(iri);
        }
        try {
            pipelineRdf = updaterFacade.update(
                    pipelineRdf, options.getPipelineIri(), options);
        } catch (UpdaterFacade.UpdateFailed ex) {
            throw new PipelineFacade.OperationFailed(
                    "Can't perform required updates.", ex);
        }
        return pipelineRdf;
    }

}
