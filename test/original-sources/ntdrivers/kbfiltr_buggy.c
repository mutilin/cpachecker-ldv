/* Generated by CIL v. 1.3.6 */
/* print_CIL_Input is true */

void errorFn(void);
void _BLAST_init(void);
void stub_driver_init(void);
int KbFilter_PnP(int DeviceObject, int Irp );
void stubMoreProcessingRequired(void);
int IofCallDriver(int DeviceObject, int Irp );
void IofCompleteRequest(int Irp, int PriorityBoost );
int KeSetEvent(int Event, int Increment, int Wait );
int KeWaitForSingleObject(int Object, int WaitReason, int WaitMode, int Alertable, int Timeout );
int KbFilter_Complete(int DeviceObject, int Irp, int Context );
int KbFilter_CreateClose(int DeviceObject, int Irp );
int KbFilter_DispatchPassThrough(int DeviceObject, int Irp );
int KbFilter_Power(int DeviceObject, int Irp );
int PoCallDriver(int DeviceObject, int Irp );
int KbFilter_InternIoCtl(int DeviceObject, int Irp );
int KernelMode;
int Executive;
int DevicePowerState = 1;
void errorFn(void) 
{ 

	{
		goto ERROR;
		ERROR: 
		return;
	}
}
int s;
int UNLOADED;
int NP;
int DC;
int SKIP1;
int SKIP2;
int MPR1;
int MPR3;
int IPC;
int pended;
int compFptr;
int compRegistered;
int lowerDriverReturn;
int setEventCalled;
int customIrp;
int myStatus;
void stub_driver_init(void) 
{ 

	{
		s = NP;
		pended = 0;
		compFptr = 0;
		compRegistered = 0;
		lowerDriverReturn = 0;
		setEventCalled = 0;
		customIrp = 0;
		return;
	}
}
void _BLAST_init(void) 
{ 

	{
		UNLOADED = 0;
		NP = 1;
		DC = 2;
		SKIP1 = 3;
		SKIP2 = 4;
		MPR1 = 5;
		MPR3 = 6;
		IPC = 7;
		s = UNLOADED;
		pended = 0;
		compFptr = 0;
		compRegistered = 0;
		lowerDriverReturn = 0;
		setEventCalled = 0;
		customIrp = 0;
		return;
	}
}
int KbFilter_PnP(int DeviceObject, int Irp ) { 
	int devExt;
	int irpStack;
	int status;
	int event;
	int DeviceObject__DeviceExtension;
	int Irp__Tail__Overlay__CurrentStackLocation;
	int irpStack__MinorFunction;
	int devExt__TopOfStack;
	int devExt__Started;
	int devExt__Removed;
	int devExt__SurpriseRemoved;
	int Irp__IoStatus__Status;
	int Irp__IoStatus__Information;
	int Irp__CurrentLocation;
	int irpSp;
	int nextIrpSp;
	int nextIrpSp__Control;
	int irpSp___0;
	int irpSp__Context;
	int irpSp__Control;

	status = 0;
	devExt = DeviceObject__DeviceExtension;
	irpStack = Irp__Tail__Overlay__CurrentStackLocation;
	irpStack__MinorFunction = 2;

	devExt__Removed = 1;
	s = NP;
	s = SKIP1;

	Irp__CurrentLocation += 1;
	Irp__Tail__Overlay__CurrentStackLocation += 1;
	IofCallDriver(devExt__TopOfStack, Irp);
	status = 0L;

	return (status);
}

int main(void)  { 
	int status;
	int irp;
	int pirp;
	int pirp__IoStatus__Status;
	int __BLAST_NONDET;
	int irp_choice;
	int devobj;

	status = 0;
	pirp = irp;
	_BLAST_init();
	s = NP;
	customIrp = 0;
	setEventCalled = customIrp;
	lowerDriverReturn = setEventCalled;
	compRegistered = lowerDriverReturn;
	pended = compRegistered;
	pirp__IoStatus__Status = 0L;
	myStatus = 0L;
	if (irp_choice == 0) {
		pirp__IoStatus__Status = 3221225659LL;
		myStatus = 3221225659LL;
	} else {

	}
	stub_driver_init();
	if (! (status >= 0)) {
		return (-1);
	} else {

	}
	__BLAST_NONDET = 3;
	status = KbFilter_PnP(devobj, pirp);

	if (pended == 1) {
		if (s == NP) {
			s = NP;
		} else {
			goto _L___2;
		}
	} else {
		_L___2: /* CIL Label */ 
		if (pended == 1) {
			if (s == MPR3) {
				s = MPR3;
			} else {
				goto _L___1;
			}
		} else {
			_L___1: /* CIL Label */ 
			if (s == UNLOADED) {

			} else {
				if (status == -1) {

				} else {
					if (s != SKIP2) {
						if (s != IPC) {
							if (s != DC) {

							} else {
								goto _L___0;
							}
						} else {
							goto _L___0;
						}
					} else {
						_L___0: /* CIL Label */ 
						if (pended == 1) {
							if (status != 259) {
								{
									errorFn();
								}
							} else {

							}
						} else {
							if (s == DC) {
								if (status == 259) {

								} else {

								}
							} else {
								if (status != lowerDriverReturn) {
									{
										// bug is here
										errorFn();
									}
								} else {

								}
							}
						}
					}
				}
			}
		}
	}
	return (status);
}
void stubMoreProcessingRequired(void) 
{ 

	{
		if (s == NP) {
			s = MPR1;
		} else {
			{
				errorFn();
			}
		}
		return;
	}
}
int IofCallDriver(int DeviceObject, int Irp ) 
{ 
	int __BLAST_NONDET;
	int returnVal2;
	int compRetStatus;
	int lcontext;

	{
		if (compRegistered == 1) {

		} else {

		}
		if (__BLAST_NONDET == 0) {
			goto switch_2_0;
		} else {
			if (__BLAST_NONDET == 1) {
				goto switch_2_1;
			} else {
				{
					goto switch_2_default;
					if (0) {
						switch_2_0: /* CIL Label */ 
						returnVal2 = 0L;
						goto switch_2_break;
						switch_2_1: /* CIL Label */ 
						returnVal2 = 3221225473LL;
						goto switch_2_break;
						switch_2_default: /* CIL Label */ 
						returnVal2 = 259L;
						goto switch_2_break;
					} else {
						switch_2_break: /* CIL Label */;
					}
				}
			}
		}
		if (s == NP) {

		} else {
			if (s == MPR1) {

			} else {
				s = SKIP2;
				lowerDriverReturn = returnVal2;

			}
		}
		return (returnVal2);
	}
}
void IofCompleteRequest(int Irp, int PriorityBoost ) 
{ 

	{
		if (s == NP) {
			s = DC;
		} else {
			{
				errorFn();
			}
		}
		return;
	}
}
int KeSetEvent(int Event, int Increment, int Wait ) 
{ int l;

{
	setEventCalled = 1;
	return (l);
}
}
int KeWaitForSingleObject(int Object, int WaitReason, int WaitMode, int Alertable, int Timeout ) 
{ int __BLAST_NONDET;

{
	if (s == MPR3) {
		if (setEventCalled == 1) {
			s = NP;
			setEventCalled = 0;
		} else {
			goto _L;
		}
	} else {
		_L: /* CIL Label */ 
		if (customIrp == 1) {
			s = NP;
			customIrp = 0;
		} else {
			if (s == MPR3) {
				{
					errorFn();
				}
			} else {

			}
		}
	}
	if (__BLAST_NONDET == 0) {
		goto switch_3_0;
	} else {
		{
			goto switch_3_default;
			if (0) {
				switch_3_0: /* CIL Label */ 
				return (0L);
				switch_3_default: /* CIL Label */;
				return (3221225473LL);
			} else {
				switch_3_break: /* CIL Label */;
			}
		}
	}
}
}
int KbFilter_Complete(int DeviceObject, int Irp, int Context ) 
{ int event;

{
	{
		event = Context;
		KeSetEvent(event, 0, 0);
	}
	return (3221225494LL);
}
}
int KbFilter_CreateClose(int DeviceObject, int Irp ) 
{ int irpStack__MajorFunction;
int devExt__UpperConnectData__ClassService;
int Irp__IoStatus__Status;
int status;
int tmp;

{
	status = myStatus;
	if (irpStack__MajorFunction == 0) {
		goto switch_4_0;
	} else {
		if (irpStack__MajorFunction == 2) {
			goto switch_4_2;
		} else {
			if (0) {
				switch_4_0: /* CIL Label */;
				if (0 == devExt__UpperConnectData__ClassService) {
					status = 3221225860UL;
				} else {

				}
				goto switch_4_break;
				switch_4_2: /* CIL Label */;
				goto switch_4_break;
			} else {
				switch_4_break: /* CIL Label */;
			}
		}
	}
	{
		Irp__IoStatus__Status = status;
		myStatus = status;
		tmp = KbFilter_DispatchPassThrough(DeviceObject, Irp);
	}
	return (tmp);
}
}
int KbFilter_DispatchPassThrough(int DeviceObject, int Irp ) 
{ int Irp__Tail__Overlay__CurrentStackLocation;
int Irp__CurrentLocation;
int DeviceObject__DeviceExtension__TopOfStack;
int irpStack;
int tmp;

{
	irpStack = Irp__Tail__Overlay__CurrentStackLocation;
	if (s == NP) {
		s = SKIP1;
	} else {
		{
			errorFn();
		}
	}
	{
		Irp__CurrentLocation += 1;
		Irp__Tail__Overlay__CurrentStackLocation += 1;
		tmp = IofCallDriver(DeviceObject__DeviceExtension__TopOfStack, Irp);
	}
	return (tmp);
}
}
int KbFilter_Power(int DeviceObject, int Irp ) 
{ int irpStack__MinorFunction;
int devExt__DeviceState;
int powerState__DeviceState;
int Irp__CurrentLocation;
int Irp__Tail__Overlay__CurrentStackLocation;
int devExt__TopOfStack;
int powerType;
int tmp;

{
	if (irpStack__MinorFunction == 2) {
		goto switch_5_2;
	} else {
		if (irpStack__MinorFunction == 1) {
			goto switch_5_1;
		} else {
			if (irpStack__MinorFunction == 0) {
				goto switch_5_0;
			} else {
				if (irpStack__MinorFunction == 3) {
					goto switch_5_3;
				} else {
					{
						goto switch_5_default;
						if (0) {
							switch_5_2: /* CIL Label */;
							if (powerType == DevicePowerState) {
								devExt__DeviceState = powerState__DeviceState;
							} else {

							}
							switch_5_1: /* CIL Label */;
							switch_5_0: /* CIL Label */;
							switch_5_3: /* CIL Label */;
							switch_5_default: /* CIL Label */;
							goto switch_5_break;
						} else {
							switch_5_break: /* CIL Label */;
						}
					}
				}
			}
		}
	}
	if (s == NP) {
		s = SKIP1;
	} else {
		{
			errorFn();
		}
	}
	{
		Irp__CurrentLocation += 1;
		Irp__Tail__Overlay__CurrentStackLocation += 1;
		tmp = PoCallDriver(devExt__TopOfStack, Irp);
	}
	return (tmp);
}
}
int PoCallDriver(int DeviceObject, int Irp ) 
{ int __BLAST_NONDET;
int compRetStatus;
int returnVal;
int lcontext;

{
	if (compRegistered) {
		{
			compRetStatus = KbFilter_Complete(DeviceObject, Irp, lcontext);
		}
		if ((unsigned long )compRetStatus == 3221225494UL) {
			{
				stubMoreProcessingRequired();
			}
		} else {

		}
	} else {

	}
	if (__BLAST_NONDET == 0) {
		goto switch_6_0;
	} else {
		if (__BLAST_NONDET == 1) {
			goto switch_6_1;
		} else {
			{
				goto switch_6_default;
				if (0) {
					switch_6_0: /* CIL Label */ 
					returnVal = 0L;
					goto switch_6_break;
					switch_6_1: /* CIL Label */ 
					returnVal = 3221225473UL;
					goto switch_6_break;
					switch_6_default: /* CIL Label */ 
					returnVal = 259L;
					goto switch_6_break;
				} else {
					switch_6_break: /* CIL Label */;
				}
			}
		}
	}
	if (s == NP) {
		s = IPC;
		lowerDriverReturn = returnVal;
	} else {
		if (s == MPR1) {
			if ((long )returnVal == 259L) {
				s = MPR3;
				lowerDriverReturn = returnVal;
			} else {
				s = NP;
				lowerDriverReturn = returnVal;
			}
		} else {
			if (s == SKIP1) {
				s = SKIP2;
				lowerDriverReturn = returnVal;
			} else {
				{
					errorFn();
				}
			}
		}
	}
	return (returnVal);
}
}
int KbFilter_InternIoCtl(int DeviceObject, int Irp ) 
{ int Irp__IoStatus__Information;
int irpStack__Parameters__DeviceIoControl__IoControlCode;
int devExt__UpperConnectData__ClassService;
int irpStack__Parameters__DeviceIoControl__InputBufferLength;
int sizeof__CONNECT_DATA;
int irpStack__Parameters__DeviceIoControl__Type3InputBuffer;
int sizeof__INTERNAL_I8042_HOOK_KEYBOARD;
int hookKeyboard__InitializationRoutine;
int hookKeyboard__IsrRoutine;
int Irp__IoStatus__Status;
int hookKeyboard;
int connectData;
int status;
int tmp;

{
	status = 0L;
	Irp__IoStatus__Information = 0;
	if (irpStack__Parameters__DeviceIoControl__IoControlCode == (((11 << 16) | (128 << 2)) | 3)) {
		goto switch_7_exp_0;
	} else {
		if (irpStack__Parameters__DeviceIoControl__IoControlCode == (((11 << 16) | (256 << 2)) | 3)) {
			goto switch_7_exp_1;
		} else {
			if (irpStack__Parameters__DeviceIoControl__IoControlCode == (((11 << 16) | (4080 << 2)) | 3)) {
				goto switch_7_exp_2;
			} else {
				if (irpStack__Parameters__DeviceIoControl__IoControlCode == 11 << 16) {
					goto switch_7_exp_3;
				} else {
					if (irpStack__Parameters__DeviceIoControl__IoControlCode == ((11 << 16) | (32 << 2))) {
						goto switch_7_exp_4;
					} else {
						if (irpStack__Parameters__DeviceIoControl__IoControlCode == ((11 << 16) | (16 << 2))) {
							goto switch_7_exp_5;
						} else {
							if (irpStack__Parameters__DeviceIoControl__IoControlCode == ((11 << 16) | (2 << 2))) {
								goto switch_7_exp_6;
							} else {
								if (irpStack__Parameters__DeviceIoControl__IoControlCode == ((11 << 16) | (8 << 2))) {
									goto switch_7_exp_7;
								} else {
									if (irpStack__Parameters__DeviceIoControl__IoControlCode == ((11 << 16) | (1 << 2))) {
										goto switch_7_exp_8;
									} else {
										if (0) {
											switch_7_exp_0: /* CIL Label */;
											if (devExt__UpperConnectData__ClassService != 0) {
												status = 3221225539UL;
												goto switch_7_break;
											} else {
												if (irpStack__Parameters__DeviceIoControl__InputBufferLength < sizeof__CONNECT_DATA) {
													status = 3221225485UL;
													goto switch_7_break;
												} else {

												}
											}
											connectData = irpStack__Parameters__DeviceIoControl__Type3InputBuffer;
											goto switch_7_break;
											switch_7_exp_1: /* CIL Label */ 
											status = 3221225474UL;
											goto switch_7_break;
											switch_7_exp_2: /* CIL Label */;
											if (irpStack__Parameters__DeviceIoControl__InputBufferLength < sizeof__INTERNAL_I8042_HOOK_KEYBOARD) {
												status = 3221225485UL;
												goto switch_7_break;
											} else {

											}
											hookKeyboard = irpStack__Parameters__DeviceIoControl__Type3InputBuffer;
											if (hookKeyboard__InitializationRoutine) {

											} else {

											}
											if (hookKeyboard__IsrRoutine) {

											} else {

											}
											status = 0L;
											goto switch_7_break;
											switch_7_exp_3: /* CIL Label */;
											switch_7_exp_4: /* CIL Label */;
											switch_7_exp_5: /* CIL Label */;
											switch_7_exp_6: /* CIL Label */;
											switch_7_exp_7: /* CIL Label */;
											switch_7_exp_8: /* CIL Label */;
											goto switch_7_break;
										} else {
											switch_7_break: /* CIL Label */;
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
	if (! (status >= 0)) {
		{
			Irp__IoStatus__Status = status;
			myStatus = status;
			IofCompleteRequest(Irp, 0);
		}
		return (status);
	} else {

	}
	{
		tmp = KbFilter_DispatchPassThrough(DeviceObject, Irp);
	}
	return (tmp);
}
}
