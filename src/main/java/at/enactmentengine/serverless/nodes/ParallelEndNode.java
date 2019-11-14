package at.enactmentengine.serverless.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.enactmentengine.serverless.model.Data;

/**
 * Control node which manages the tasks at the end of a parallel loop.
 * 
 * @author markusmoosbrugger, jakobnoeckl
 *
 */
public class ParallelEndNode extends Node {
	final static Logger logger = LoggerFactory.getLogger(ParallelEndNode.class);
	private int waitcounter = 0;
	private List<Data> output = new ArrayList<>();
	private Map<String, Object> parallelResult = new HashMap<>();
	private Node currentCopy;

	public ParallelEndNode(String name, String type, List<Data> output) {
		super(name, type);
		this.output = output;
	}

	/**
	 * Counts the number of invocations and resumes with passing the results to the
	 * children if all parents have finished.
	 */
	@Override
	public Boolean call() throws Exception {
		synchronized (this) {
			if (++waitcounter != parents.size()) {
				return false;
			}
		}

		Map<String, Object> outputValues = new HashMap<>();
		if (output != null) {
			for (Data data : output) {
				String key = name + "/" + data.getName();
				if (parallelResult.containsKey(data.getSource())) {
					outputValues.put(key, parallelResult.get(data.getSource()));
				} else {
					for (Entry<String, Object> inputElement : parallelResult.entrySet()) {
						if (data.getSource().contains(inputElement.getKey())) {
							if (data.getType().equals("collection")) {
								// combines all results from the executed branches into one collection
								outputValues.put(key, parallelResult);
								break;
							} else {
								outputValues.put(key, inputElement.getValue());
							}

						}

					}
				}
			}
			logger.info("Executing " + name + " ParallelEndNode with output: " + outputValues.toString());

		}

		for (Node node : children) {
			node.passResult(outputValues);
			node.call();

		}
		return true;
	}

	/**
	 * Retrieves the results from the different parents and set them as result.
	 */
	@Override
	public void passResult(Map<String, Object> input) {
		synchronized (this) {
			if (output == null)
				return;
			for (Data data : output) {
				if (input.containsKey(data.getSource())) {
					parallelResult.put(data.getSource(), input.get(data.getSource()));
				}
				for (Entry<String, Object> inputElement : input.entrySet()) {
					if (data.getSource().contains(inputElement.getKey())) {
						parallelResult.put(inputElement.getKey(), input.get(inputElement.getKey()));
					}
				}
			}

		}

	}

	/**
	 * Returns the result.
	 */
	@Override
	public Map<String, Object> getResult() {
		return parallelResult;
	}

	/**
	 * Clones this node and its children. Cloning is needed for ParallelFor
	 * branches.
	 */
	@Override
	public Node clone(Node endNode) throws CloneNotSupportedException {
		if (endNode == currentCopy) {
			return endNode;
		}

		Node node = (Node) super.clone();
		node.children = new ArrayList<>();
		for (Node childrenNode : children) {
			node.children.add((Node) childrenNode.clone(endNode));
		}
		currentCopy = node;
		return node;

	}

}
