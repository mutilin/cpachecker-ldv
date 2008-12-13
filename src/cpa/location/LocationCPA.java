package cpa.location;

import cfa.objectmodel.CFAFunctionDefinitionNode;
import cpa.common.interfaces.AbstractDomain;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.ConfigurableProgramAnalysis;
import cpa.common.interfaces.MergeOperator;
import cpa.common.interfaces.Precision;
import cpa.common.interfaces.PrecisionAdjustment;
import cpa.common.interfaces.StopOperator;
import cpa.common.interfaces.TransferRelation;
import exceptions.CPAException;

public class LocationCPA implements ConfigurableProgramAnalysis{

	private AbstractDomain abstractDomain;
	private TransferRelation transferRelation;
	private MergeOperator mergeOperator;
	private StopOperator stopOperator;
	private PrecisionAdjustment precisionAdjustment;

	public LocationCPA (String mergeType, String stopType) throws CPAException{
	  LocationDomain locationDomain = new LocationDomain ();
	  TransferRelation locationTransferRelation = new LocationTransferRelation (locationDomain);
	  MergeOperator locationMergeOp = null;
	  if(mergeType.equals("sep")){
	    locationMergeOp = new LocationMergeSep (locationDomain);
	  }
	  if(mergeType.equals("join")){
	    throw new CPAException("Location domain elements cannot be joined");
	  }
	  StopOperator locationStopOp = new LocationStopSep (locationDomain);
	  PrecisionAdjustment precisionAdjustment = new LocationPrecisionAdjustment ();
	  
		this.abstractDomain = locationDomain;
		this.transferRelation = locationTransferRelation;
		this.mergeOperator = locationMergeOp;
		this.stopOperator = locationStopOp;
		this.precisionAdjustment = precisionAdjustment;
	}

	public AbstractDomain getAbstractDomain ()
	{
	  return abstractDomain;
	}

  public TransferRelation getTransferRelation ()
  {
    return transferRelation;
  }

	public MergeOperator getMergeOperator ()
	{
	  return mergeOperator;
	}

	public StopOperator getStopOperator ()
	{
	  return stopOperator;
	}

  public PrecisionAdjustment getPrecisionAdjustment () {
    return precisionAdjustment;
  }

	public AbstractElement getInitialElement (CFAFunctionDefinitionNode node) {
	  return new LocationElement (node);
	}

  public Precision getInitialPrecision (CFAFunctionDefinitionNode pNode) {
    return new LocationPrecision();
  }
}
