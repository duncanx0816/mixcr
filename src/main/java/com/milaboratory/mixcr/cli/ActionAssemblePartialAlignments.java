package com.milaboratory.mixcr.cli;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters;
import com.milaboratory.util.SmartProgressReporter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionAssemblePartialAlignments implements Action {
    private final AssemblePartialAlignmentsParameters parameters = new AssemblePartialAlignmentsParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();
        PartialAlignmentsAssemblerParameters assemblerParameters = PartialAlignmentsAssemblerParameters.getDefault();

        if (!parameters.overrides.isEmpty()) {
            assemblerParameters = JsonOverrider.override(assemblerParameters, PartialAlignmentsAssemblerParameters.class, parameters.overrides);
            if (assemblerParameters == null) {
                System.err.println("Failed to override some parameter.");
                return;
            }
        }

        long start = System.currentTimeMillis();
        try (PartialAlignmentsAssembler assembler = new PartialAlignmentsAssembler(assemblerParameters, parameters.getOutputFileName(),
                parameters.getWritePartial(), parameters.getOverlappedOnly())) {
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFileName())) {
                SmartProgressReporter.startProgressReport("Building index", reader);
                assembler.buildLeftPartsIndex(reader);
            }
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFileName())) {
                SmartProgressReporter.startProgressReport("Searching for overlaps", reader);
                assembler.searchOverlaps(reader);
            }

            if (parameters.report != null)
                Util.writeReport(parameters.getInputFileName(), parameters.getOutputFileName(),
                        helper.getCommandLineArguments(), parameters.report, assembler,
                        System.currentTimeMillis() - start);

            long time = System.currentTimeMillis() - beginTimestamp;

            // Writing report to stout
            System.out.println("============= Report ==============");
            Util.writeReportToStdout(assembler, time);
        }
    }

    @Override
    public String command() {
        return "assemblePartial";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Parameters(commandDescription = "Assemble clones")
    private static class AssemblePartialAlignmentsParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file output_file")
        public List<String> parameters;

        @DynamicParameter(names = "-O", description = "Overrides default parameter values.")
        public Map<String, String> overrides = new HashMap<>();

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "Write only overlapped sequences (needed for testing).",
                names = {"-o", "--overlapped-only"})
        public Boolean overlappedOnly;

        @Parameter(description = "Write partial sequences (for recurrent overlapping).",
                names = {"-p", "--write-partial"})
        public Boolean writePartial;

        public String getInputFileName() {
            return parameters.get(0);
        }

        public String getOutputFileName() {
            return parameters.get(1);
        }

        public Boolean getOverlappedOnly() {
            return overlappedOnly != null && overlappedOnly;
        }

        public Boolean getWritePartial() {
            return writePartial != null && writePartial;
        }

        @Override
        protected List<String> getOutputFiles() {
            return Collections.singletonList(getOutputFileName());
        }

        @Override
        public void validate() {
            if (parameters.size() != 2)
                throw new ParameterException("Wrong number of parameters.");
            super.validate();
        }

    }
}
