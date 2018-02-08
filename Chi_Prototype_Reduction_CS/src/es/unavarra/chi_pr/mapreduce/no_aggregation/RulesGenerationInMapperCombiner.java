/*
 * Copyright (C) 2015 Mikel Elkano Ilintxeta
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.unavarra.chi_pr.mapreduce.no_aggregation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.StringTokenizer;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import es.unavarra.chi_pr.core.FuzzyRule;
import es.unavarra.chi_pr.core.Mediator;
import es.unavarra.chi_pr.utils.ByteArrayWritable;
import es.unavarra.chi_pr.utils.CombiningFunction;
import es.unavarra.chi_pr.utils.InMapperCombiner;
import es.unavarra.chi_pr.utils.LongArrayWritable;

public class RulesGenerationInMapperCombiner extends Mapper<Text, Text, ByteArrayWritable, LongArrayWritable> {
	
	private int i;
	private String[] exampleStr;
	private String [] inputValues;
	private byte[] labels;
	private byte[] antecedents;
	private long[] numExamples;
	
	private long startMs, endMs;
	
	private final InMapperCombiner combiner = new InMapperCombiner<ByteArrayWritable, LongArrayWritable>(
        new CombiningFunction<LongArrayWritable>() {
        	private LongArrayWritable newArray;
        	private int counter;
            @Override
            public LongArrayWritable combine(LongArrayWritable value1, LongArrayWritable value2) {
                newArray = new LongArrayWritable(value1.getData());
                for (counter = 0; counter < newArray.getData().length; counter++)
                	newArray.getData()[counter] += value2.getData()[counter];
                return newArray;
            }
        }
    );
	
	@Override
	protected void cleanup(Context context) throws IOException, InterruptedException {
		
		combiner.flush(context);
		
		// Write execution time
		endMs = System.currentTimeMillis();
		long mapperID = context.getTaskAttemptID().getTaskID().getId();
		try {
        	FileSystem fs = FileSystem.get(Mediator.getConfiguration());
        	Path file = new Path(Mediator.getHDFSLocation()+Mediator.getOutputPath()+Mediator.TIME_STATS_DIR+"/mapper"+mapperID+".txt");
        	OutputStream os = fs.create(file);
        	BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
        	bw.write("Execution time (seconds): "+((endMs-startMs)/1000));
        	bw.close();
        	os.close();
        }
        catch(Exception e){
        	System.err.println("\nMAP PHASE: ERROR WRITING EXECUTION TIME");
			e.printStackTrace();
        }
		
	}

	@Override
	public void map(Text key, Text value, Context context) throws IOException, InterruptedException {
		
		// Read example
        StringTokenizer st = new StringTokenizer(key.toString()+value.toString(), ", ");
        for (i = 0; i < Mediator.getNumInputVariables(); i++){ // Read input values
            exampleStr[i] = st.nextToken();
            inputValues[i] = exampleStr[i];
        }
        exampleStr[Mediator.getNumInputVariables()] = st.nextToken(); // Read class label
		
        // Generate fuzzy rule
        labels = FuzzyRule.getRuleFromExample(exampleStr);
        antecedents = new byte[labels.length-1];
        for (i = 0; i < antecedents.length; i++)
        	antecedents[i] = labels[i];
        
        // Count number of examples for the class of the rule
        for (i = 0; i < numExamples.length; i++)
			numExamples[i] = 0;
        numExamples[labels[labels.length-1]] = 1;
        
        /*
    	 * Key: Antecedents of the rule
    	 * Value: Class of the rule
    	 */
        combiner.write(new ByteArrayWritable(antecedents), new LongArrayWritable(numExamples), context);
		
	}
	
	@Override
	protected void setup(Context context) throws InterruptedException, IOException{

		super.setup(context);
		
		startMs = System.currentTimeMillis();
		
		try {
			Mediator.setConfiguration(context.getConfiguration());
			Mediator.readConfiguration();
		}
		catch(Exception e){
			System.err.println("\nMAP PHASE: ERROR READING CONFIGURATION: "+e.getMessage()+"\n");
			e.printStackTrace();
			System.exit(-1);
		}
		
		// Initialize structures
		exampleStr = new String[Mediator.getNumInputVariables()+1];
		inputValues = new String[Mediator.getNumInputVariables()];
		numExamples = new long[Mediator.getNumClasses()];
	
	}

}
