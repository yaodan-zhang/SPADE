/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.storage.neo4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import spade.query.quickgrail.core.QueryEnvironment;
import spade.query.quickgrail.entities.Graph;
import spade.query.quickgrail.entities.GraphMetadata;
import spade.query.quickgrail.utility.TreeStringSerializable;
import spade.storage.Neo4j;
import spade.utility.HelperFunctions;

public class Neo4jQueryEnvironment extends TreeStringSerializable implements QueryEnvironment{

	private final Logger logger = Logger.getLogger(this.getClass().getName());
	
	private final String symbolNodeLabel = "spade_symbols";
	private final String propertyNameIdCounter = "id_counter";
	private final String propertyNameGraphSymbols = "graph";
	private final String propertyNameGraphMetadataSymbols = "graph_metadata";

	private final String baseString = "base";
	private final String baseVariable = "$" + baseString;
	private final Graph kBaseGraph;
	
	private Integer idCounter = null;
	private final HashMap<String, Graph> graphSymbols = new HashMap<String, Graph>();
	private final HashMap<String, GraphMetadata> graphMetadataSymbols = new HashMap<String, GraphMetadata>();

	private final Neo4j storage;
	public final String mainVertexLabel;
	public final String edgeSymbolsPropertyKey;
	
	public Neo4jQueryEnvironment(Neo4j storage, 
			String mainVertexLabel,
			String edgeSymbolsPropertyKey){
		this.storage = storage;
		this.mainVertexLabel = mainVertexLabel;
		this.edgeSymbolsPropertyKey = edgeSymbolsPropertyKey;
		this.kBaseGraph = new Graph(mainVertexLabel);
		initialize(false);
	}

	@Override
	public String getBaseSymbolName(){
		return baseVariable;
	}
	
	@Override
	public Graph getBaseGraph(){
		return kBaseGraph;
	}
	
	@Override
	public Set<String> getAllGraphSymbolNames(){
		return graphSymbols.keySet();
	}
	
	private boolean symbolNodeExists(){
		return storage.executeQueryForSmallResult(
				"match (v:" + symbolNodeLabel + ") return v limit 1;"
				).size() > 0;
	}

	private Integer symbolNodeGetPropertyIdCounter(){
		List<Map<String, Object>> result = storage.executeQueryForSmallResult(
				"match (v:" + symbolNodeLabel + ") return v." + propertyNameIdCounter + 
				" as " + propertyNameIdCounter + " limit 1;");
		if(result.size() > 0){
			return HelperFunctions.parseInt(String.valueOf(result.get(0).get(propertyNameIdCounter)), null);
		}else{
			return null;
		}
	}

	private String symbolNodeGetPropertyGraph(){
		List<Map<String, Object>> result = storage.executeQueryForSmallResult(
				"match (v:" + symbolNodeLabel + ") return v." + propertyNameGraphSymbols + 
				" as " + propertyNameGraphSymbols + " limit 1;");
		if(result.size() > 0){
			return String.valueOf(result.get(0).get(propertyNameGraphSymbols));
		}else{
			return null;
		}
	}

	private String symbolNodeGetPropertyGraphMetadata(){
		List<Map<String, Object>> result = storage.executeQueryForSmallResult(
				"match (v:" + symbolNodeLabel + ") return v." + propertyNameGraphMetadataSymbols + 
				" as " + propertyNameGraphMetadataSymbols + " limit 1;");
		if(result.size() > 0){
			return String.valueOf(result.get(0).get(propertyNameGraphMetadataSymbols));
		}else{
			return null;
		}
	}

	private Map<String, String> inflateMapFromString(String str){
		Map<String, String> map = new HashMap<String, String>();
		if(!HelperFunctions.isNullOrEmpty(str)){
			String[] tokens = str.split(",");
			for(String token : tokens){
				String[] keyValue = token.split("=");
				if(keyValue.length == 2){
					map.put(keyValue[0].trim(), keyValue[1].trim());
				}
			}
		}
		return map;
	}

	private String deflateGraphSymbolsToString(Map<String, Graph> map){
		String str = "";
		for(Map.Entry<String, Graph> entry : map.entrySet()){
			str += entry.getKey() + "=" + entry.getValue().name + ",";
		}
		if(!str.isEmpty()){
			str = str.substring(0, str.length() - 1);
		}
		return str;
	}

	private String deflateGraphMetadataSymbolsToString(Map<String, GraphMetadata> map){
		String str = "";
		for(Map.Entry<String, GraphMetadata> entry : map.entrySet()){
			str += entry.getKey() + "=" + entry.getValue().name + ",";
		}
		if(!str.isEmpty()){
			str = str.substring(0, str.length() - 1);
		}
		return str;
	}

	public synchronized void initialize(boolean deleteFirst){
		if(deleteFirst){
			storage.executeQueryForSmallResult("match (x:" + symbolNodeLabel + ") delete x;");
			idCounter = null;
			graphSymbols.clear();
			graphMetadataSymbols.clear();
		}

		if(!symbolNodeExists()){
			storage.executeQueryForSmallResult(
					"create (x:" + symbolNodeLabel + "{"
					+ propertyNameIdCounter + ":0, "
					+ propertyNameGraphSymbols + ":'', "
					+ propertyNameGraphMetadataSymbols + ":''});");
		}

		idCounter = symbolNodeGetPropertyIdCounter();
		if(idCounter == null){
			idCounter = 0;
			storage.executeQueryForSmallResult("match (x:" + symbolNodeLabel + ") "
					+ "set x." + propertyNameIdCounter + "=" + String.valueOf(idCounter) + ";");
		}

		String graphProperty = symbolNodeGetPropertyGraph();
		if(!HelperFunctions.isNullOrEmpty(graphProperty)){
			Map<String, String> map = inflateMapFromString(graphProperty);
			for(Map.Entry<String, String> entry : map.entrySet()){
				graphSymbols.put(entry.getKey(), new Graph(entry.getValue()));
			}
		}

		String graphMetadataProperty = symbolNodeGetPropertyGraphMetadata();
		if(!HelperFunctions.isNullOrEmpty(graphMetadataProperty)){
			Map<String, String> map = inflateMapFromString(graphMetadataProperty);
			for(Map.Entry<String, String> entry : map.entrySet()){
				graphMetadataSymbols.put(entry.getKey(), new GraphMetadata(entry.getValue()));
			}
		}

		logger.log(Level.SEVERE, "Neo4j:Symbols:ID-Counter="+idCounter+", Graph="+ graphSymbols +", GraphMetadata="+ graphMetadataSymbols);
	}
	
	@Override
	public void clear(){
		initialize(true);
		gc(); // TODO can be done quicker since we have all the valid names
	}
	
	private Set<String> getAllVertexLabels(){
		final Set<String> allLabels = new HashSet<String>();
		final List<Map<String, Object>> callDbLabelsResult = storage.executeQueryForSmallResult("call db.labels();");
		for(final Map<String, Object> map : callDbLabelsResult){
			allLabels.add(String.valueOf(map.get("label")));
		}
		return allLabels;
	}
	
	private Set<String> getAllEdgeLabels(){
		final Set<String> allEdgeSymbols = new HashSet<String>();
		final List<Map<String, Object>> edgeSymbolsPropertyValues = storage.executeQueryForSmallResult(
				"match ()-[e]->() with distinct e."
						+ edgeSymbolsPropertyKey + " as "
						+ edgeSymbolsPropertyKey + " return "
						+ edgeSymbolsPropertyKey
				);
		for(final Map<String, Object> map : edgeSymbolsPropertyValues){
			final Object object = map.get(edgeSymbolsPropertyKey);
			if(object != null){ // ignore the null i.e. only exists in base
				final String edgeSymbolsList = String.valueOf(object);
				final String[] edgeSymbolsTokens = edgeSymbolsList.split(",");
				for(final String edgeSymbolsToken : edgeSymbolsTokens){
					if(!edgeSymbolsToken.isEmpty()){
						allEdgeSymbols.add(edgeSymbolsToken);
					}
				}
			}
		}
		return allEdgeSymbols;
	}
	
	public void dropVertexLabels(String... labels){
		if(labels != null){
			String queryLabels = "";
			for(String label : labels){
				if(!HelperFunctions.isNullOrEmpty(label)){
					queryLabels += ":" + label.trim();
				}
			}
			queryLabels = queryLabels.trim(); // format = ":<a>:<b>"
			
			if(!queryLabels.isEmpty()){
				storage.executeQueryForSmallResult("match(n) remove n" + queryLabels + " ;");
			}
		}
	}
	
	public void dropEdgeSymbol(String symbol){
		if(!HelperFunctions.isNullOrEmpty(symbol)){
			symbol = symbol.trim();
			String matchClause = "match ()-[e]->()";
			String whereClause = "where e." + edgeSymbolsPropertyKey + " contains ',"+symbol+",'";
			String setClause = "set e." + edgeSymbolsPropertyKey + " = replace(e." + edgeSymbolsPropertyKey+ ", "
					+ "',"+symbol+",', '');";
			String query = matchClause + " " + whereClause + " " + setClause;
			storage.executeQuery(query);
		}
	}
	
	@Override
	public void gc(){
		// Get all non-garbage labels
		final Set<String> graphVariables = new HashSet<String>();
		for(final Graph graph : graphSymbols.values()){
			graphVariables.add(graph.name);
		}
		
		final Set<String> nonGarbageVariables = new HashSet<String>();
		nonGarbageVariables.addAll(graphVariables);
		nonGarbageVariables.add(mainVertexLabel);
		nonGarbageVariables.add(symbolNodeLabel);
		
		// Get all labels
		final Set<String> allLabels = getAllVertexLabels();
		
		// Collect garbage labels
		final Set<String> garbageLabels = new HashSet<String>();
		for(String allLabel : allLabels){
			if(isGraphOrGraphMetadataName(allLabel)){
				garbageLabels.add(allLabel);
			}
		}
		garbageLabels.removeAll(nonGarbageVariables);
		
		// Build the query to delete the garbage labels
		dropVertexLabels(garbageLabels.toArray(new String[]{}));
		
		// Get all symbols from the property
		final Set<String> allEdgeSymbols = getAllEdgeLabels();
		
		final Set<String> garbageEdgeSymbols = new HashSet<String>();
		for(String allEdgeSymbol : allEdgeSymbols){
			if(isGraphOrGraphMetadataName(allEdgeSymbol)){
				garbageEdgeSymbols.add(allEdgeSymbol);
			}
		}
		garbageEdgeSymbols.removeAll(graphVariables);
		
		if(!garbageEdgeSymbols.isEmpty()){
			for(String garbageEdgeSymbol : garbageEdgeSymbols){
				dropEdgeSymbol(garbageEdgeSymbol);
			}
		}
	}

	//////////
	private String createGraphName(String suffix){
		return "graph_" + suffix;
	}

	private String createGraphMetadataName(String suffix){
		return "meta_" + suffix;
	}

	private boolean isGraphOrGraphMetadataName(String name){
		return name.startsWith("graph_") || name.startsWith("meta_");
	}
	//////////

	private synchronized String incrementIdCounter(){
		idCounter++;
		String result = String.valueOf(idCounter);
		storage.executeQuery("match (x:" + symbolNodeLabel + ") "
				+ "set x." + propertyNameIdCounter + "=" + result + ";");
		return result;
	}

	@Override
	public Graph allocateGraph(){
		return new Graph(createGraphName(incrementIdCounter()));
	}

	@Override
	public GraphMetadata allocateGraphMetadata(){
		return new GraphMetadata(createGraphMetadataName(incrementIdCounter()));
	}

	@Override
	public Graph lookupGraphSymbol(String symbol){
		switch(symbol){
			case baseVariable: return kBaseGraph;
		}
		return graphSymbols.get(symbol);
	}

	@Override
	public GraphMetadata lookupGraphMetadataSymbol(String symbol){
		// @<name>
		return graphMetadataSymbols.get(symbol);
	}

	@Override
	public void setGraphSymbol(String symbol, Graph graph){
		switch(symbol){
		case baseVariable:
			throw new RuntimeException("Cannot reassign reserved variables.");
		}
		boolean update;
		Graph existing = graphSymbols.get(symbol);
		if(existing != null){
			if(existing.equals(graph)){
				update = false;
			}else{
				update = true;
			}
		}else{
			update = true;
		}
		if(update){
			graphSymbols.put(symbol, graph);
			storage.executeQuery("match (x:" + symbolNodeLabel + ") "
					+ "set x." + propertyNameGraphSymbols + "='" 
					+ deflateGraphSymbolsToString(graphSymbols) + "';");
		}
	}

	@Override
	public void setGraphMetadataSymbol(String symbol, GraphMetadata graphMetadata){
		boolean update;
		GraphMetadata existing = graphMetadataSymbols.get(symbol);
		if(existing != null){
			if(existing.equals(graphMetadata)){
				update = false;
			}else{
				update = true;
			}
		}else{
			update = true;
		}
		if(update){
			graphMetadataSymbols.put(symbol, graphMetadata);
			storage.executeQuery("match (x:" + symbolNodeLabel + ") "
					+ "set x." + propertyNameGraphMetadataSymbols + "='"
					+ deflateGraphMetadataSymbolsToString(graphMetadataSymbols) + "';");
		}
	}

	@Override
	public void eraseGraphSymbol(String symbol){
		switch(symbol){
			case baseVariable: throw new RuntimeException("Cannot erase reserved variables.");
		}
		if(graphSymbols.containsKey(symbol)){
			graphSymbols.remove(symbol);
			storage.executeQuery("match (x:" + symbolNodeLabel + ") "
					+ "set x." + propertyNameGraphSymbols + "='" 
					+ deflateGraphSymbolsToString(graphSymbols) + "';");
		}
	}

	@Override
	public void eraseGraphMetadataSymbol(String symbol){
		if(graphMetadataSymbols.containsKey(symbol)){
			graphMetadataSymbols.remove(symbol);
			storage.executeQuery("match (x:" + symbolNodeLabel + ") "
					+ "set x." + propertyNameGraphMetadataSymbols + "='"
					+ deflateGraphMetadataSymbolsToString(graphMetadataSymbols) + "';");
		}
	}

	@Override
	public boolean isBaseGraph(Graph graph){
		return graph.equals(kBaseGraph);
	}
	
	@Override
	public String getLabel(){
		return "Environment";
	}

	@Override
	protected void getFieldStringItems(ArrayList<String> inline_field_names, ArrayList<String> inline_field_values,
			ArrayList<String> non_container_child_field_names,
			ArrayList<TreeStringSerializable> non_container_child_fields, ArrayList<String> container_child_field_names,
			ArrayList<ArrayList<? extends TreeStringSerializable>> container_child_fields){
		for(Entry<String, Graph> entry : graphSymbols.entrySet()){
			inline_field_names.add(entry.getKey());
			inline_field_values.add(entry.getValue().name);
		}
		for(Entry<String, GraphMetadata> entry : graphMetadataSymbols.entrySet()){
			inline_field_names.add(entry.getKey());
			inline_field_values.add(entry.getValue().name);
		}
	}
	
	private void printSymbolsNode(){
		logger.log(Level.INFO, "Neo4j Query Environment: " + 
				storage.executeQueryForSmallResult(
						"match (v:" + symbolNodeLabel + ") "
								+ "return "
								+ "v."+propertyNameIdCounter+" as "+propertyNameIdCounter+", "
								+ "v."+propertyNameGraphSymbols+" as "+propertyNameGraphSymbols+", "
								+ "v."+propertyNameGraphMetadataSymbols+" as "+propertyNameGraphMetadataSymbols+" "
								+ "limit 1;"
						)
		);
	}
	// match (a:spade_symbols) delete a;
	// match (a:spade_symbols) return a; // must be one
	// create (a:spade_symbols{id_counter:1, graph:"", graph_metadata:""})
	// match (a:spade_symbols) set a.graph='$a=hasjkd,$x=123';

	// get all edge property keys: match ()-[e]->() unwind keys(e) as ee return collect(distinct ee) as kk
	// get all vertex property keys: match (n) unwind keys(n) as nn return collect(distinct nn) as jj
	
}
