/*
 * Copyright (c) 2017 Villu Ruusmann
 *
 * This file is part of JPMML-SkLearn
 *
 * JPMML-SkLearn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-SkLearn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-SkLearn.  If not, see <http://www.gnu.org/licenses/>.
 */
package sklearn2pmml.preprocessing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.FieldColumnPair;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MapValues;
import org.dmg.pmml.OpType;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FeatureUtil;
import org.jpmml.converter.PMMLEncoder;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;
import org.jpmml.sklearn.ClassDictUtil;
import org.jpmml.sklearn.SkLearnEncoder;
import sklearn.Transformer;
import sklearn.TypeUtil;

public class LookupTransformer extends Transformer {

	public LookupTransformer(String module, String name){
		super(module, name);
	}

	@Override
	public OpType getOpType(){
		return OpType.CATEGORICAL;
	}

	@Override
	public DataType getDataType(){
		Map<?, ?> mapping = getMapping();

		List<Object> inputValues = new ArrayList<>(mapping.keySet());

		return TypeUtil.getDataType(inputValues, DataType.STRING);
	}

	@Override
	public List<Feature> encodeFeatures(List<Feature> features, SkLearnEncoder encoder){
		Map<?, ?> mapping = getMapping();
		Object defaultValue = getDefaultValue();

		List<String> columns = formatColumns(features);

		ClassDictUtil.checkSize(features.size() + 1, columns);

		MapValues mapValues = new MapValues();

		List<String> inputColumns = columns.subList(0, columns.size() - 1);

		for(int i = 0; i < features.size(); i++){
			Feature feature = features.get(i);
			String inputColumn = inputColumns.get(i);

			mapValues.addFieldColumnPairs(new FieldColumnPair(feature.getName(), inputColumn));
		}

		String outputColumn = columns.get(columns.size() - 1);

		mapValues.setOutputColumn(outputColumn);

		Map<String, List<Object>> data = parseMapping(inputColumns, outputColumn, mapping);

		mapValues.setInlineTable(PMMLUtil.createInlineTable(data));

		List<Object> outputValues = new ArrayList<>();
		outputValues.addAll(data.get(outputColumn));

		if(defaultValue != null){
			mapValues.setDefaultValue(ValueUtil.formatValue(defaultValue));

			outputValues.add(defaultValue);
		}

		FieldName name = FeatureUtil.createName("lookup", features);

		DerivedField derivedField = encoder.createDerivedField(name, OpType.CATEGORICAL, TypeUtil.getDataType(outputValues, DataType.STRING), mapValues);

		Feature feature = new Feature(encoder, derivedField.getName(), derivedField.getDataType()){

			@Override
			public ContinuousFeature toContinuousFeature(){
				PMMLEncoder encoder = getEncoder();

				DerivedField derivedField = (DerivedField)encoder.toContinuous(getName());

				return new ContinuousFeature(encoder, derivedField);
			}
		};

		return Collections.singletonList(feature);
	}

	protected List<String> formatColumns(List<Feature> features){
		ClassDictUtil.checkSize(1, features);

		return Arrays.asList("data:input", "data:output");
	}

	protected Map<String, List<Object>> parseMapping(List<String> inputColumns, String outputColumn, Map<?, ?> mapping){
		List<Object> inputValues = new ArrayList<>();
		List<Object> outputValues = new ArrayList<>();

		Collection<? extends Map.Entry<?, ?>> entries = mapping.entrySet();
		for(Map.Entry<?, ?> entry : entries){
			Object inputValue = entry.getKey();
			Object outputValue = entry.getValue();

			if(inputValue == null){
				throw new IllegalArgumentException();
			} // End if

			if(outputValue == null){
				continue;
			}

			inputValues.add(inputValue);
			outputValues.add(outputValue);
		}

		String inputColumn = inputColumns.get(0);

		Map<String, List<Object>> result = new LinkedHashMap<>();
		result.put(inputColumn, inputValues);
		result.put(outputColumn, outputValues);

		return result;
	}

	public Map<?, ?> getMapping(){
		return get("mapping", Map.class);
	}

	public Object getDefaultValue(){
		return getOptionalObject("default_value");
	}
}
