package model;

import io.GeneralPreferences;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import model.VEdge;
import model.VNode;
import model.Messages.GraphMessage;
/**
 * VGraph encapsulates an MGraph and keeps visual information about every node, edge and subset in the MGraph
 * each manipulation on the VGraph is also given to the MGraph
 * The MGraph may the extracted and being used to generate another VGraph to the same MGraph
 *
 * The VGraph is observable, so that every Observer may update itself, if something changes here
 *
 * the update may give Information about the updated parts by the GraphMessage
 *
 * @author Ronny Bergmann 
 */
public class VGraph extends Observable implements Observer {

	private MGraph mG;
	public VNodeModification modifyNodes;
	public VEdgeModification modifyEdges;
	public VSubSetModification modifySubSets;
	/**
	 * Constructor
	 * 
	 * @param d indicates whether the graph is directed or not
	 * @param l indicates whether the graph might have loops or not
	 * @param m indicates whether the graph might have multiple edges between two nodes or not
	 */	
	public VGraph(boolean d, boolean l, boolean m)
	{
		mG = new MGraph(d,l,m);
		modifyNodes = new VNodeModification(mG);
		modifyEdges = new VEdgeModification(mG);
		modifySubSets = new VSubSetModification(mG);
		modifyNodes.addObserver(modifyEdges); //Edges must react on NodeDeletions
		modifySubSets.addObserver(modifyNodes); //Nodes must react on SubSetChanges
		modifySubSets.addObserver(modifyEdges); //Edges must react on SubSetChanges
		//SubSet has not to react on anything, because the Membership is kept im mG and not in modifySubSets
		modifyNodes.addObserver(this);
		modifyEdges.addObserver(this);
		modifySubSets.addObserver(this);
	}
	/**
	 * deselect all Nodes and Edges
	 */
	public void deselect() {
		modifyNodes.deselect();
		modifyEdges.deselect();
		setChanged();
		notifyObservers(new GraphMessage(GraphMessage.SELECTION,GraphMessage.UPDATE));
	}
	/**
	 * deletes all selected Nodes and Edges. That means, that also all incident Edges of selected Nodes are deleted
	 * TODO
	 */
	public void removeSelection()
	{
		setChanged();
		notifyObservers(
			new GraphMessage(GraphMessage.SELECTION|GraphMessage.NODE|GraphMessage.EDGE, //Changed
							GraphMessage.REMOVAL|GraphMessage.BLOCK_START, //Status 
							GraphMessage.NODE|GraphMessage.EDGE|GraphMessage.SELECTION) //Affected		
			);
		Iterator<VNode> n = modifyNodes.getNodeIterator();
		HashSet<VNode> selected = new HashSet<VNode>();
		while (n.hasNext()) {
			VNode node = n.next();
			if ((node.getSelectedStatus()&VItem.SELECTED)==VItem.SELECTED)
				selected.add(node);
		}
		n = selected.iterator();
		while (n.hasNext())
		{
			modifyNodes.removeNode(n.next().getIndex());
		}
		Iterator<VEdge> n2 = modifyEdges.getEdgeIterator();
		HashSet<VEdge> selected2 = new HashSet<VEdge>();
		while (n2.hasNext()) {
			VEdge edge = n2.next();
			if ((edge.getSelectedStatus() & VItem.SELECTED) == VItem.SELECTED)
				selected2.add(edge);
		}
		n2 = selected2.iterator();
		while (n2.hasNext())
		{
			modifyEdges.removeEdge(n2.next().getIndex());
		}
		setChanged();
		notifyObservers(
			new GraphMessage(GraphMessage.SELECTION|GraphMessage.NODE|GraphMessage.EDGE, //Changed
							GraphMessage.REMOVAL|GraphMessage.BLOCK_END, //Status 
							GraphMessage.NODE|GraphMessage.EDGE|GraphMessage.SELECTION) //Affected		
		);		
	}
	/**
	 *  Modify the Graph to the given directed or undirected Value.
	 *  <br>If modifying to undirected, some edges may be deleted (if and only if two edges from a to b and b to a exist)
	 *  <br><br>
	 *  TODO
	 * @param d
	 */
	public BitSet setDirected(boolean d)
	{
		BitSet removed = new BitSet();
		if (d==mG.isDirected())
			return removed; //nicht geändert
		//Auf gerihctet umstellen ist kein Problem. 
		if (!d) //also falls auf ungerichtet umgestellt wird
		{
			if (!mG.isMultipleAllowed()) //Ist auch nur ein Problem, wenn keine Mehrfachkanten erlaubt sind
			{
				//modifyNodes.NodeLock.lock(); //Knoten finden
//				try
//				{
					Iterator<VNode> n = modifyNodes.getNodeIterator();
					while (n.hasNext())
					{
						VNode t = n.next();
						Iterator<VNode> n2 = modifyNodes.getNodeIterator();			//jeweils
						while (n2.hasNext())
						{
							VNode t2 = n2.next();
							if (t.getIndex() < t2.getIndex())
							{
								Vector<Integer> ttot2 = mG.getEdgeIndices(t.getIndex(),t2.getIndex());
								Vector<Integer> t2tot = mG.getEdgeIndices(t2.getIndex(),t.getIndex());
								//In the nonmultiple case each Vector has exactely one or no edge in it
								if ((!ttot2.isEmpty())&&(!t2tot.isEmpty()))
								{
									int e1 = ttot2.firstElement();
									int e2 = t2tot.firstElement();
									MEdge m = mG.getEdge(e2);
									m.Value = mG.getEdge(e2).Value+mG.getEdge(e1).Value;
								//	mG.replaceEdge(m); Notify is pushed for MGraph at the end of the method
									modifyEdges.removeEdge(e1);
									removed.set(e1);
								}
							} //End no duplicate
						}
					}
	//			}	
	//			finally {modifyNodes.NodeLock.unlock();}
				if (mG.setDirected(d).cardinality() > 0)
				{
					System.err.println("DEBUG ; Beim gerichtet Setzen läuft was falsch");
				} 				
			} //end of if !allowedmultiple
			else //multiple allowed - the other way around
			{
				if (mG.setDirected(d).cardinality() > 0)
				{
					System.err.println("DEBUG ; Beim gerichtet Setzen läuft was falsch");
				} 				
			//	modifyEdges.getEdgeIterator(); //find similar Edges
			//	try
			//	{		
					HashSet<VEdge> toDelete = new HashSet<VEdge>(); // zu entfernende Kanten
					Iterator<VEdge> e = modifyEdges.getEdgeIterator();				
					while (e.hasNext())
					{
						VEdge t = e.next();
						int ts = mG.getEdge(t.getIndex()).StartIndex;
						int te = mG.getEdge(t.getIndex()).StartIndex;
						Vector<Integer> indices = mG.getEdgeIndices(ts,te);
						Iterator<Integer> iiter = indices.iterator();
						while (iiter.hasNext())
						{
							VEdge act = modifyEdges.getEdge(iiter.next());
							if ((mG.getEdge(act.getIndex()).StartIndex==te)&&(!mG.isDirected())&&(act.getType()==VEdge.ORTHOGONAL)&&(t.getType()==VEdge.ORTHOGONAL)) 
							//ungerichtet, beide orthogonal und entgegengesetz gespeichert
							{
								if ((((VOrthogonalEdge)act).getVerticalFirst()!=((VOrthogonalEdge)t).getVerticalFirst())&&(!removed.get(act.getIndex())))
								{
									//System.err.println("removing Edge #"+t.index+" because ORTH and it is similar to #"+act.index);
									toDelete.add(t);
									removed.set(t.getIndex());
								}
							}
							else if ((t.PathEquals(act)&&(!removed.get(act.getIndex())))&&(t.getIndex()!=act.getIndex())) //same path
							{
								//System.err.println("removing Edge #"+t.index+" because it is similar to #"+act.index);
								toDelete.add(t);
								removed.set(t.getIndex());
							}
						} //end inner while
					} //end outer while
					Iterator<VEdge> e3 = toDelete.iterator();
					while (e3.hasNext())
						modifyEdges.removeEdge_(e3.next().getIndex());
		//		} finally{modifyEdges..unlock();}
			} //end of deleting similar edges in multiple directed graphs
		}//end if !d
		else //undirected
			mG.setDirected(d); //change
		//im MGraph auch noch
		setChanged();
		notifyObservers(
				new GraphMessage(GraphMessage.EDGE|GraphMessage.DIRECTION, //Type
								GraphMessage.UPDATE) //Status 
			);
		return removed;
	}
	/**
	 * Translates the Graph by the given Offset in x and y direction
	 * <br><br>
	 * @param x Translation on the X-axis
	 * @param y Translation on the Y-axis
	 */
	public void translate(int x, int y)
	{
		Iterator<VNode> iter1 = modifyNodes.getNodeIterator();
		while (iter1.hasNext())
		{
			iter1.next().translate(x, y);
		}
		Iterator<VEdge> iter2 = modifyEdges.getEdgeIterator();
		while(iter2.hasNext())
		{
			iter2.next().translate(x,y);
		}
		setChanged();
		notifyObservers(
				new GraphMessage(GraphMessage.NODE|GraphMessage.EDGE, //Type
								GraphMessage.TRANSLATION, //Status 
								GraphMessage.NODE|GraphMessage.EDGE|GraphMessage.SELECTION|GraphMessage.SUBSET) //Affected		
			);
	}
	/**
	 * Get the Math-Graph underneath this VGraph
	 * <br><br>
	 * @return a referenceto the MGraph
	 */
	public MGraph getMathGraph() {
		return mG;
	}
	/**
	 * replace the actual VGraph with another one.
	 * <br>Use this Method to replace this VGraph with a new loaded one or a new visualised one.
	 * <br>
	 * <br>The pushNotify Should be used to indicate complete replacement to reset observers
	 * @param anotherone the New VGraph
	 */
	public void replace(VGraph anotherone)
	{
		//Del Me
		modifyNodes.deleteObserver(this); modifyEdges.deleteObserver(this); modifySubSets.deleteObserver(this);
		//But keep themselfs observed by each other
		//Replacement
		modifyNodes =anotherone.modifyNodes;
		modifyEdges = anotherone.modifyEdges;
		modifySubSets = anotherone.modifySubSets;

		//Renew actual stuff
		modifyNodes.addObserver(modifyEdges); //Edges must react on NodeDeletions
		modifySubSets.addObserver(modifyNodes); //Nodes must react on SubSetChanges
		modifySubSets.addObserver(modifyEdges); //Edges must react on SubSetChanges
		//SubSet has not to react on anything, because the Membership is kept im mG and not in modifySubSets
		modifyNodes.addObserver(this);
		modifyEdges.addObserver(this);
		modifySubSets.addObserver(this);

		mG = anotherone.mG;
		mG.pushNotify(
						new GraphMessage(GraphMessage.ALL_ELEMENTS, //Type
										GraphMessage.REPLACEMENT, //Status 
										GraphMessage.ALL_ELEMENTS) //Affected		
		);
		setChanged();
		notifyObservers(
				new GraphMessage(GraphMessage.ALL_ELEMENTS, //Type
						GraphMessage.REPLACEMENT, //Status 
						GraphMessage.ALL_ELEMENTS) //Affected		
		);
	}
	/**
	 * Clone the VGraph and
	 * @return the copy
	 */
	public VGraph clone()
	{
		VGraph clone = new VGraph(mG.isDirected(),mG.isLoopAllowed(),mG.isMultipleAllowed());
		//Knoten
		Iterator<VNode> n2 = modifyNodes.getNodeIterator();
		while (n2.hasNext())
		{
			VNode nodeclone = n2.next().clone();
			clone.modifyNodes.addNode(nodeclone, mG.getNode(nodeclone.getIndex()));
			//In alle Sets einfuegen
			Iterator<VSubSet>n1 = modifySubSets.getSubSetIterator();
			while (n1.hasNext())
			{
				VSubSet actualSet = n1.next();
				if (mG.getSubSet(actualSet.index).containsNode(nodeclone.getIndex()))
					clone.modifySubSets.addNodetoSubSet(nodeclone.getIndex(), actualSet.index); //In jedes Set setzen wo er war
			}
		}
		//Analog Kanten
		Iterator<VEdge> n3 = modifyEdges.getEdgeIterator();
		while (n3.hasNext())
		{
			VEdge cloneEdge = n3.next().clone();
			MEdge me = mG.getEdge(cloneEdge.getIndex());
			clone.modifyEdges.addEdge(cloneEdge, me, modifyNodes.getNode(me.StartIndex).getPosition(), modifyNodes.getNode(me.EndIndex).getPosition());
			//In alle Sets einfuegen
			Iterator<VSubSet> n1 = modifySubSets.getSubSetIterator();
			while (n1.hasNext())
			{
				VSubSet actualSet = n1.next();
				if (mG.getSubSet(actualSet.index).containsEdge(cloneEdge.getIndex()))
					clone.modifySubSets.addEdgetoSubSet(cloneEdge.getIndex(), actualSet.getIndex()); //Jedes Set kopieren
			}
		}
		//Untergraphen
		Iterator<VSubSet> n1 = modifySubSets.getSubSetIterator();
		while (n1.hasNext())
		{
			VSubSet actualSet = n1.next();
			clone.modifySubSets.addSubSet(actualSet, mG.getSubSet(actualSet.index)); //Jedes Set kopieren
		}
		//und zurückgeben
		return clone;
	}
	/**
	 * returns the maximum point that is used by the VGraph.
	 * <br>On nodes the size of the node is included
	 * <br>On Edges the control point is included 
	 * 
	 * @return Maximum as a point
	 */
	public Point getMaxPoint(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g;
		Point maximum = new Point(0,0);
		Iterator<VNode> iter1 = modifyNodes.getNodeIterator();
		while (iter1.hasNext())
		{
			VNode actual = iter1.next();
			Point p = (Point) actual.getPosition().clone();
			p.translate(Math.round(actual.getSize()/2), Math.round(actual.getSize()/2));
			p.x++; p.y++;
			if (p.x > maximum.x)
				maximum.x = p.x;
			if (p.y > maximum.y)
				maximum.y = p.y;
//			Node Text - only if Graphics are not null, they are null if there is no visible picture. 
			//And if node name is visible
			if ((g2!=null)&&(actual.isNameVisible()))
			{
				Font f = new Font("Arial",Font.PLAIN, Math.round(actual.getNameSize()));
				//mittelpunkt des Textes
				int x = actual.getPosition().x + Math.round((float)actual.getNameDistance()*(float)Math.cos(Math.toRadians((double)actual.getNameRotation())));
				int y = actual.getPosition().y - Math.round((float)actual.getNameDistance()*(float)Math.sin(Math.toRadians((double)actual.getNameRotation())));
			
				FontMetrics metrics = g2.getFontMetrics(f);
				int hgt = metrics.getAscent()-metrics.getLeading()+metrics.getDescent();
				int adv = metrics.stringWidth(mG.getNode(actual.getIndex()).name);
				x += new Double(Math.floor((double)adv/2.0d)).intValue(); y += new Double(Math.floor((double)hgt/2.0d)).intValue(); //Bottom Right Corner
				if (x > maximum.x)
					maximum.x = x;
				if (y > maximum.y)
					maximum.y = y;
			}
		}
		Iterator<VEdge> iter2 = modifyEdges.getEdgeIterator();
		while(iter2.hasNext())
		{
			Point edgemax = iter2.next().getMax();
			if (edgemax.x > maximum.x)
				maximum.x = edgemax.x;
			if (edgemax.y > maximum.y)
				maximum.y = edgemax.y;
		}
		return maximum;
	}
	/**
	 * returns the minimum point that is used by the VGraph.
	 * <br>On nodes the size of the node and the size of the text is included
	 * <br>On Edges the control point is included
	 * <br>The Graphics are needed to compute the fontsize
	 * <br>Zoom is not encalculated 
	 * <br>
	 * @param the Graphic in which the Graph lies. 
	 * @return Point MinPoint
	 */
	public Point getMinPoint(Graphics g)
	{
		Graphics2D g2 = (Graphics2D)g; 
		Point minimum = new Point(Integer.MAX_VALUE,Integer.MAX_VALUE);
		Iterator<VNode> iter1 = modifyNodes.getNodeIterator();
		while (iter1.hasNext())
		{
			VNode actual = iter1.next();
			Point p = (Point) actual.getPosition().clone();
			p.translate(-Math.round(actual.getSize()/2), -Math.round(actual.getSize()/2));
			if (p.x>0) p.x--; 
			if (p.y>0) p.y--; 
			if (p.x < minimum.x)
				minimum.x = p.x;
			if (p.y < minimum.y)
				minimum.y = p.y;
			//Node Text - only if Graphics are not null, they are null if there is no visible picture. 
			if ((g2!=null)&&(actual.isNameVisible()))
			{
				Font f = new Font("Arial",Font.PLAIN, Math.round(actual.getNameSize()));
				//mittelpunkt des Textes
				int x = actual.getPosition().x + Math.round((float)actual.getNameDistance()*(float)Math.cos(Math.toRadians((double)actual.getNameRotation())));
				int y = actual.getPosition().y - Math.round((float)actual.getNameDistance()*(float)Math.sin(Math.toRadians((double)actual.getNameRotation())));
			
				FontMetrics metrics = g2.getFontMetrics(f);
				int hgt = metrics.getAscent()-metrics.getLeading()+metrics.getDescent();
				int adv = metrics.stringWidth(mG.getNode(actual.getIndex()).name);
				x -= new Double(Math.floor((double)adv/2.0d)).intValue(); y -= new Double(Math.floor((double)hgt/2.0d)).intValue(); //Top Left Corner
				if (x < minimum.x)
					minimum.x = x;
				if (y < minimum.y)
					minimum.y = y;
			}
		}
		Iterator<VEdge> iter2 = modifyEdges.getEdgeIterator();
		while(iter2.hasNext())
		{
			Point edgemax = iter2.next().getMin();
			if (edgemax.x < minimum.x)
				minimum.x = edgemax.x;
			if (edgemax.y < minimum.y)
				minimum.y = edgemax.y;
		}
		return minimum;
	}
	/**
	 * Set the possibility of multiple edges to the new value
	 * If multiple edges are disabled, the multiple edges are removed and the edge values between two nodes are added
	 * @param a
	 */
	public BitSet setMultipleAllowed(boolean b)
	{
		BitSet removed = new BitSet();
		if ((mG.isMultipleAllowed())&&(!b)) //Changed from allowed to not allowed, so remove all multiple
		{	
			setChanged();
			//Allowance Updated, affected the edges
			notifyObservers(new GraphMessage(GraphMessage.MULTIPLE,GraphMessage.UPDATE,GraphMessage.EDGE));	
			Iterator<VNode> n = modifyNodes.getNodeIterator();				
			while (n.hasNext())
			{
				VNode t = n.next();
				Iterator<VNode> n2 = modifyNodes.getNodeIterator();
				while (n2.hasNext())
				{
					VNode t2 = n2.next();
					//if the graph is directed
					if (((!mG.isDirected())&&(t2.getIndex()<=t.getIndex()))||(mG.isDirected())) //in the nondirected case only half the cases
					{
						if (mG.EdgesBetween(t.getIndex(),t2.getIndex())>1) //we have to delete
						{
							Vector<Integer> multipleedges = mG.getEdgeIndices(t.getIndex(),t2.getIndex());
							int value = mG.getEdge(multipleedges.firstElement()).Value;
							//Add up the values and remove the edges from the second to the last
							Iterator<Integer> iter = multipleedges.iterator();
							iter.next();
							while(iter.hasNext())
							{
								int nextindex = iter.next();
								value += mG.getEdge(nextindex).Value;
								modifyEdges.removeEdge(nextindex);
								removed.set(nextindex);
							}
							MEdge e = mG.getEdge(multipleedges.firstElement());
							e.Value = value;
							//mG.replaceEdge(e); Notify is pushed below
						}
					}					
				}
			}
		} //End complicated rebuild
		if (b!=mG.isMultipleAllowed())
		{
			if (mG.setMultipleAllowed(b).cardinality() > 0)
			{
				System.err.println("DEBUG : AllowMultiple set to false ERROR on that");
			}
			setChanged();
			//Allowance Updated, affected the edges
			notifyObservers(new GraphMessage(GraphMessage.MULTIPLE,GraphMessage.UPDATE|GraphMessage.BLOCK_END,GraphMessage.EDGE));	
		}
		return removed;
	}
	/**
	 * informs all subscribers about a change. This Method is used to push a notify from outside
	 * mit dem Oject o als Parameter
	 */
	public void pushNotify(Object o) {
		setChanged();
		if (o == null)
			notifyObservers();
		else
			notifyObservers(o);
	}	
	/**
	 * get the edge in Range of a given point.
	 * an edge is in Range, if the distance from the edge-line or line segments to the point p is smaller than the edge width
	 * <br><br>
	 * <i>not very exact at the moment</i>
	 * 
	 * @param p a point
	 * @param variation the variation m may be away from the edge
	 * @return the first edge in range, if there is one, else null
	 */
	public VEdge getEdgeinRange(Point m, double variation) {
		variation *=(float)GeneralPreferences.getInstance().getIntValue("vgraphic.zoom")/100; //jop is gut
		Iterator<VEdge> n = modifyEdges.getEdgeIterator();
		while (n.hasNext()) {
			VEdge temp = n.next();
			// naechste Kante
			MEdge me = mG.getEdge(temp.getIndex());
			Point p1 = (Point)modifyNodes.getNode(me.StartIndex).getPosition().clone();
			Point p2 = (Point)modifyNodes.getNode(me.EndIndex).getPosition().clone();
			// getEdgeShape
			GeneralPath p = temp.getPath(p1, p2,1.0f); //no zoom on check!
		    PathIterator path = p.getPathIterator(null, 0.001); 
		    // 0.005 = the flatness; reduce if result is not accurate enough!
		    double[] coords = new double[2];
		    double x = 0.0, y = 0.0, lastx=0.0, lasty = 0.0;
		    double closestDistanceSquare = Double.MAX_VALUE;
		    while( !path.isDone() ) 
		    {
		    	int type = path.currentSegment(coords);
		    	x = coords[0]; y = coords[1];
		    	switch(type)
		    	{
		    		case PathIterator.SEG_LINETO:
		    		{
		    			double v = variation + (float)temp.width;
		    			Rectangle2D.Double r = new Rectangle.Double(m.x-v/2,m.y-v/2,v,v);
		    			Line2D.Double l = new Line2D.Double(lastx,lasty,x,y);
		    			if (l.intersects(r))
		    			{
		    				return temp;
		    			}
		    			break;
		    		}
		    		case PathIterator.SEG_MOVETO: break;
		    		default:
		    		{
				    	//System.err.print("("+new Double(x).intValue()+","+new Double(y).intValue()+") ");
				    	double distanceSquare = Point.distanceSq(x,y,m.x,m.y);
				    	if (distanceSquare < (variation+(float)temp.width)) 
				    	{
					    		return temp;
					    }		    			
		    		}
		    	}
		    	lastx = x; lasty = y;
		    	path.next();
		    }
		    //if the shortest distance is smaller than  
		    if (closestDistanceSquare < (variation+(float)temp.width))
		    	return temp;
		}
		return null; // keinen gefunden
	}
	/**
	 * add edges from evey selected node to a given node
	 * @param Ende
	 * 				the target of all new edges
	 */
	public void addEdgesfromSelectedNodes(VNode Ende) {
		pushNotify(new GraphMessage(GraphMessage.EDGE,GraphMessage.ADDITION|GraphMessage.BLOCK_START));
		Iterator<VNode> iter = modifyNodes.getNodeIterator();
		while (iter.hasNext()) 
		{
				VNode temp = iter.next();
				if (((temp.getSelectedStatus()&VItem.SELECTED)==VItem.SELECTED) && (temp != Ende)) 
				{
					int i = mG.getNextEdgeIndex();
					//Standard ist eine StraightLineEdge
					MEdge me;
					if (Ende.getIndex()==0)
						me = new MEdge(i,temp.getIndex(),Ende.getIndex(),GeneralPreferences.getInstance().getIntValue("edge.value"),"\u22C6");
					else
						me = new MEdge(i,temp.getIndex(),Ende.getIndex(),GeneralPreferences.getInstance().getIntValue("edge.value"),GeneralPreferences.getInstance().getEdgeName(i, temp.getIndex(), Ende.getIndex()));					
						modifyEdges.addEdge(new VStraightLineEdge(i,GeneralPreferences.getInstance().getIntValue("edge.width")), me,modifyNodes.getNode(temp.getIndex()).getPosition(), Ende.getPosition());
				}
		}
		pushNotify(new GraphMessage(GraphMessage.EDGE,GraphMessage.BLOCK_END));
	}
	/**
	 * add edges from a given node to evey selected node
	 * @param Start 
	 * 				the source of all new edges
	 */
	public void addEdgestoSelectedNodes(VNode Start) {
		pushNotify(new GraphMessage(GraphMessage.EDGE,GraphMessage.ADDITION|GraphMessage.BLOCK_START));
		Iterator<VNode> iter = modifyNodes.getNodeIterator();
		while (iter.hasNext()) 
		{
				VNode temp = iter.next();
				if (((temp.getSelectedStatus() & VItem.SELECTED) == VItem.SELECTED) && (temp != Start)) 
				{
					int i = mG.getNextEdgeIndex();
					//Standard ist eine StraightLineEdge
					MEdge me;
					if (Start.getIndex()==0)
						me = new MEdge(i,Start.getIndex(),temp.getIndex(),GeneralPreferences.getInstance().getIntValue("edge.value"),"\u22C6");
					else
						me = new MEdge(i,Start.getIndex(),temp.getIndex(),GeneralPreferences.getInstance().getIntValue("edge.value"),GeneralPreferences.getInstance().getEdgeName(i, Start.getIndex(), temp.getIndex()));
					
						modifyEdges.addEdge(
								new VStraightLineEdge(i,GeneralPreferences.getInstance().getIntValue("edge.width")), 
								me,
								Start.getPosition(),
								temp.getPosition());
				}
		}
		pushNotify(new GraphMessage(GraphMessage.EDGE,GraphMessage.BLOCK_END));
	}
	public void update(Observable o, Object arg) 
	{
		if (arg instanceof GraphMessage) //Not nice but haven't found a beautiful way after hours
		{
			GraphMessage m = (GraphMessage)arg;
			if (m!=null) //send all GraphChange-Messages to Observers of the VGraph
			{
				setChanged();
				notifyObservers(m);
			}
		}
	}
}