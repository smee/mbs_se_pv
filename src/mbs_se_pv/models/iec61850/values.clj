(ns mbs-se-pv.models.iec61850.values)

(def si-unit-kinds 
  {:ampSecond {:notes "amp seconds (As)", :unit "As", :value 68},
   :ampSquare {:notes "amps squared (A2)", :unit "A²", :value 69},
   :ampSquareSecond {:notes "amps squared time (A2s)", :unit "A²t", :value 70},
   :ampere {:notes "current", :unit "A", :value 5},
   :becquerel {:notes "activity (l/s)", :unit "Bq", :value 22},
   :candela {:notes "luminous intensity", :unit "cd", :value 8},
   :cosTheta {:notes "power factor", :unit "cosθ", :value 65},
   :coulomb {:notes "electric charge (AS)", :unit "C", :value 26},
   :cubicMeter {:notes "volume", :unit "m3", :value 42},
   :cubicMeterPerSecond {:notes "volumetric flow rate", :unit "m³/s", :value 45},
   :degree {:notes "plane angle", :unit "deg", :value 9},
   :degreeCelsius {:notes "relative temperature", :unit "°C", :value 23},
   :dimensionless {:notes "no dimension", :value 1},
   :farad {:notes "electric capacitance (C/V)", :unit "F", :value 25},
   :gray {:notes "absorbed dose (J/Kg)", :unit "Gy", :value 21},
   :henry {:notes "electric inductance (Wb/A)", :unit "H", :value 28},
   :hertz {:notes "frequency (1/s)", :unit "Hz", :value 33},
   :joule {:notes "energy (N m)", :unit "J", :value 31},
   :joulePerKelvin {:notes "heat capacity", :unit "J/K", :value 51},
   :kelvin {:notes "temperature", :unit "K", :value 6},
   :kilogram {:notes "mass", :unit "kg", :value 3},
   :kilogramMeter {:notes "moment of mass (kg m)", :unit "M", :value 47},
   :kilogramPerCubicMeter {:notes "density", :unit "kg/m³", :value 48},
   :lumen {:notes "luminous flux (cd sr)", :unit "Lm", :value 35},
   :lux {:notes "illuminance (lm / m2)", :unit "lx", :value 34},
   :meter {:notes "length", :unit "m", :value 2},
   :meterPerCubicMeter {:notes "fuel efficiency", :unit "m/m3", :value 46},
   :meterPerSecond {:notes "velocity", :unit "m/s", :value 43},
   :meterPerSquareSecond {:notes "Acceleration", :unit "m/s²", :value 44},
   :mole {:notes "amount of substance", :unit "mol", :value 7},
   :newton {:notes "force (kg m / s2)", :unit "N", :value 32},
   :ohm {:notes "electric resistance (VA)", :unit "Ω", :value 30},
   :partPerMillion {:notes "concentration", :unit "ppm", :value 52},
   :pascal {:notes "pressure (N / m2)", :unit "Pa", :value 39},
   :phaseAngleDegree {:notes "phase angle", :unit "θ", :value 64},
   :radian {:notes "plane angle", :unit "rad", :value 10},
   :radianPerSecond {:notes "angular velocity", :unit "rad/s", :value 54},
   :rotationPerSecond {:notes "rotational speed", :unit "1/s", :value 53},
   :second {:notes "time", :unit "s", :value 4},
   :siemens {:notes "electric conductance (A/V)", :unit "S", :value 27},
   :sievert {:notes "dose equivalent (J/kg)", :unit "Sv", :value 24},
   :squareMeter {:notes "area", :unit "m2", :value 41},
   :squareMeterPerSecond {:notes "viscosity", :unit "m²/s", :value 49},
   :steradian {:notes "solid angle", :unit "sr", :value 11},
   :tesla {:notes "magnetic flux density (Wb / m2)", :unit "T", :value 37},
   :volt {:notes "electric potential (W/A)", :unit "V", :value 29},
   :voltAmpere {:notes "apparent power", :unit "VA", :value 61},
   :voltAmpereHour {:notes "apparent energy", :unit "VAh", :value 71},
   :voltAmpereReactive {:notes "reactive power (VISinTheta)", :unit "VAr", :value 63},
   :voltAmpereReactiveHour {:notes "reactive energy", :unit "VArh", :value 73},
   :voltPerHertz {:notes "magnetic flux", :unit "V/Hz", :value 74},
   :voltSecond {:notes "volt seconds (Ws / A)", :unit "Vs", :value 66},
   :voltSquare {:notes "volts squared (W2 / A²)", :unit "V²", :value 67}
   :watt {:notes "real power (I2R)", :unit "W", :value 62},
   :wattGeneral {:notes "power (J /s)", :unit "W", :value 38},
   :wattHour {:notes "real energy", :unit "Wh", :value 72},
   :wattPerMeterKelvin {:notes "thermal conductivity", :unit "W/mK", :value 50},
   :weber {:notes "magnetic flux (V s)", :unit "Wb", :value 36}
   })

(def abbrev-7-4
  {"A" "Current"
"Acs" "Access"
"Abr" "Abrasion"
"Abs" "Absolute"
"AC" " AC, alternating current"
"Acc" "Accuracy"
"Act" "Action, activity"
"Acu" "Acoustic"
"Adj" "Adjustment"
"Adp" "Adapter, adaptation"
"Age" "Ageing"
"Air" "Air"
"Alg" "Algorithm"
"Alm" "Alarm"
"Amp" "Current non-phase-related"
"An" "Analogue"
"Ang" "Angle"
"Ap" "Access point"
"App" "Apparent"
"Arc" "Arc"
"Area" "Area"
"Auth" "Authorisation"
"Auto" "Automatic"
"Aux" "Auxiliary"
"Av" "Average"
"AWatt" "Wattmetric component of current"
"Ax" "Axial"
"B" "Bushing"
"Base" "Base"
"Bat" "Battery"
"Beh" "Behaviour"
"Ber" " Bit error rate"
"Bias" "Bias"
"Bin" "Binary"
"Blb" "Bulb"
"Blk" "Block, blocked"
"Bnd" "Band"
"Bo" "Bottom"
"Bst" "Boost"
"Bus" "Bus"
"C" "Carbon"
"C2H2" "Acetylene"
"C2H4" "Ethylene"
"C2H6" "Ethane"
"Cap" "Capability"
"Capac" "Capacitance"
"Car" "Carrier"
"CB" "Circuit breaker"
"Cdt" "Credit"
"CE" "Cooling equipment"
"Cel" "Cell"
"Cf" "Crest factor"
"Cff" "Coefficient"
"Cfg" "Configuration"
"CG" "Core ground"
"Ch" "Channel"
"CH4" "Methane"
"Cha" "Charger"
"Chg" "Change"
"Chk" "Check"
"Chr" "Characteristic"
"Circ" "Circulating, circuit"
"Clc" "Calculate, calculated"
"Clk" "Clock, clockwise"
"Cloud" "Cloud"
"Clr" "Clear"
"Cls" "Close"
"Cndct" "Conductivity"
"Cnt" "Counter"
"Cmbu" "Combustible, combustion"
"Cmd" "Command"
"CO" "Carbon monoxide"
"CO2" "Carbon dioxide"
"Col" "Coil"
"Conf" "Configuration"
"Cons" "Constant"
"Con" "Contact"
"Cor" "Correction"
"Core" "Core"
"Crd" "Coordination"
"Crit" "Critical"
"Crv" "Curve"
"CT" "Current transducer"
"Ctl" "Control"
"Ctr" "Center"
"Cur" "Current"
"Cvr" " Cover, cover level"
"Cyc" "Cycle"
"D" "Derivate"
"Day" "Day"
"dB" "Decibel"
"Dct" "Direct"
"Dea" "Dead"
"Den" "Density"
"Det" "Detected"
"Detun" "Detuning"
"DExt" "De-excitation"
"Dew" "Dew"
"Dff" "Diffuse"
"Dgr" "Degree"
"Diag" "Diagnostics"
"Dif" "Differential, difference"
"Dip" "Dip"
"Dir" "Direction"
"Dis" "Distance"
"Dsp" "Displacement"
"Dl" "Delay"
"Dlt" "Delete"
"Dmd" "Demand"
"Dn" "Down"
"DPCSO" "Double point controllable status output"
"DQ0" "Direct, quadrature, and zero axis quantities" 
"Drag" "Drag hand"
"Drv" "Drive"
"DS" "Device state"
"Dsc" "Discrepancy"
"Dsch" "Discharge"
"Dur" "Duration"
"Dv" "Deviation"
"EC" "Earth Coil"
"Echo" "Echo"
"EE" "External equipment"
"EF" "Earth fault"
"Emg" "Emergency"
"Ems" "Emissions"
"En" "Energy"
"Ena" "Enabled"
"End" "End"
"Env" "Environment"
"Eq" "Equalization, equal"
"Err" "Error"
"Ev" "Evaluation"
"Evt" "Event"
"Ex" "External"
"Exc" "Exceeded"
"Excl" "Exclusion"
"Exp" "Expired"
"Ext" "Excitation"
"F" "Float"
"FA" "Fault arc"
"Fact" "Factor"
"Fail" "Failure"
"Fan" "Fan"
"Fer" "Frame error rate"
"Fil" "Filter, filtration"
"Fish" "Fish"
"Fld" "Field"
"Fll" "Fall"
"Flood" "Flood"
"Flt" "Fault"
"Flush" "Flush"
"Flw" "Flow"
"FPF" "Forward power flow"
"Fu" "Fuse"
"Full" "Full"
"Fwd" "Forward"
"Gas" "Gas"
"Gen" "General"
"Go" "Goose"
"Goose" "control block reference (see IEC 61850-7-2)"
"Gn" "Generator"
"Gnd" "Ground"
"Gr" "Group"
"Grd" "Guard"
"Grn" "Green"
"Gri" "Grid"
"Gust" "Gust"
"H" "Harmonics (phase-related)"
"H2" "Hydrogen"
"H2O" "Water"
"Ha" "Harmonics (non-phase-related)"
"Health" "Health"
"Heat" "Heater, heating"
"Hi" "High, highest"
"Hor" "Horizontal"
"HP" "Hot point"
"Hum" "Humidity"
"Hy" " Hydraulics, hydraulic system"
"Hyd" " Hydrological, hydro, water"
"Hz" "Frequency"
"I" "Integral"
"Imb" "Imbalance"
"Imp" "Impedance non-phase-related"
"In" "Input"
"Ina" "Inactivity"
"Iner" "Inertia"
"Incr" "Increment"
"Ind" "Indication"
"Inh" "Inhibit"
"Ins" "Insulation"
"Insol" "Insolation"
"Int" "Integer"
"Intr" "Interrupt, interruption"
"Intv" "Interval"
"ISCSO" "Integer status controllable status output"
"K" "Constant"
"Kck" "Kicker"
"Key" "Key"
"km" "Kilometre"
"L" "Lower"
"Last" "Last"
"Ld" "Lead"
"LD" "Logical device"
"LDC" "Line drop compensation"
"LDCR" "Line drop compensation resistance"
"LDCX" "Line drop compensation reactance"
"LDCZ" "Line drop compensation impedance"
"Leak" "Leakage"
"LED" "Light-emitting diode"
"Len" "Length"
"Lev" "Level"
"Lg" "Lag"
"Lim" "Limit"
"Lin" "Line"
"Liv" "Live"
"LN" "Logical node"
"Lo" "Low"
"LO" "Lockout"
"Loc" "Local"
"Lod" "Load, loading"
"Lok" "Locked"
"Loop" "Loop"
"Los" "Loss"
"Lst" "List"
"LTC" " Load tap changer"
"M" "Minutes"
 "M/O/C" "Data object is mandatory or optional or conditional" 
"Made" "Made"
"Mag" "Magnetic, magnitude"
"Max" "Maximum"
"Mbr" "Membrane"
"Mem" "Memory"
"Min" "Minimum"
"Mir" "Mirror"
"Mlt" "Multiplier, multiple"
"Mod" "Mode"
"Month" "Month"
"Mot" "Motor"
"Ms" "Milliseconds"
"Mst" "Moisture"
"MT" "Main tank"
"Mth" "Method"
"Mvm" "Movement, moving"
"N2" "Nitrogen dioxide"
"Nam" "Name"
"Name" "Name (see Note)"
"NdsCom" "Needs commissioning (see IEC 61850-7-2)"
"Net" "Net sum"
"Neut" "Neutral"
"Nit" "Nitrogen"
"Ng" "Negative"
"Nom" "Nominal, normalising"
"Num" "Number"
"NSQ" "Average partial discharge current"
"O2" "Oxygen"
"O3" "Ozon, trioxygen"
"Ofs" "Offset"
"Oil" "Oil"
"Oo" "Out of"
"Op" "Operate, operating"
"Opn" "Open"
"Out" "Output"
"Ov" "Over, override, overflow"
"Ovl" "Overload"
"P" "Proportional"
"Pa" "Partial"
"Pap" "Paper"
"Par" "Parallel"
"Pct" "Percent, percentage"
"Per" "Periodic, period"
"PF" "Power factor"
"Ph" "Phase"
"PH" " Acidity, value of pH"
"PhsA" "Phase L1"
"PhsB" "Phase L2"
"PhsC" "Phase L3"
"PNV" "Phase-to-neutral voltage"
"Phy" "Physical"
"Pi" "Instantaneous P"
"Pls" "Pulse"
"Plt" " Plate, long-term flicker severity"
"Pmp" "Pump"
"Po" "Polar"
"Pol" "Polarizing"
"Pos" "Position"
"PosA" "Position phase L1"
"PosB" "Position phase L2"
"PosC" "Position phase L3"
"Pot" "Potentiometer"
"POW" "Point on wave switching"
"PP" "Phase to phase"
"ppm" "Parts per million"
"PPV" "Phase to phase voltage"
"Pre" "Pre-"
"Pres" "Pressure"
"Prg" "Progress, in progress"
"Pri" "Primary"
"Pro" "Protection"
"Proxy" "Proxy"
"Prt" "Parts, part"
"Ps" "Positive"
"Pst" " Post, short-term flicker severity"
"Pt" "Point"
"Pwr" "Power"
"Qty" "Quantity"
"R" "Raise"
"R0" "Zero sequence resistance"
"Rat" "Ratio"
"Rcd" "Record, recording"
"Rch" "Reach"
"Rcl" "Reclaim"
"Rct" "Reaction"
"Rdy" "Ready"
"Re" "Retry"
"React" "Reactance, reactive"
"Rec" "Reclose"
"Rect" "Rectifier"
"Red" "Reduction, redundant"
"Ref" "Reference"
"Rel" "Release"
"Rem" "Remote"
"Res" "Residual"
"Reso" "Resonance"
"Rev" "Revision"
"Rf" "Refreshment"
"Ris" "Resistance"
"Rl" "Relation, relative"
"Rmp" "Ramping, ramp"
"RMS" " Root mean square"
"Rnbk" "Runback"
"Rot" "Rotation, rotor"
"Rs" "Reset, resetable"
"Rsl" "Result"
"Rst" "Restraint, restriction"
"Rsv" "Reserve"
"Rte" "Rate"
"Rtg" "Rating"
"Rv" "Reverse"
"Rx" "Receive, received"
"S1" "Step one"
"S10" "coefficient S1.0"
"S12" "coefficient S1.2"
"S2" "Step two"
"Sar" "Surge arrestor"
"Sat" "Saturation"
"Sbs" "Subscription"
"Sch" "Scheme"
"Sco" " Supply change over"
"SCSM" " Specific communication service mapping"
"Sec" "Security"
"Sel" "Select"
"Seq" "Sequence"
"Set" "Setting"
"Sig" "Signal"
"Sign" "Sign"
"Sim" "Simulation, simulated"
"Sh" "Shunt"
"Slnt" " Salinity, saline content"
"Smok" "Smoke"
"Snr" " Signal to noise ratio"
"Snw" "Snow"
"Spd" "Speed"
"Spec" "Spectra"
"SPl" "Single pole"
"SPCSO" " Single point controllable status output"
"Spt" "Setpoint"
"Src" "Source"
"St" "Status, state"
"Sta" "Station"
"Step" "Step"
"Sto" " Storage e.g. activity of storing data"
"Stat" "Statistics"
"Stop" "Stop"
"Std" "Standard"
"Stk" "Stroke"
"Str" "Start"
"Stuck" "Stuck"
"Sup" "Supply"
"Svc" "Service"
"SvCBRef" ""
"SV" "control block reference (see IEC 61850-7-2)"
"Sw" "Switch, switched"
"Swg" "Swing"
"Syn" "Synchronisation"
"Tap" "Tap"
"Td" "Total distortion"
"Tdf" " Transformer derating factor"
"Tdp" "Td’ "
"Td0P" "Td0’"
"Td0S" "Td0’’"
"Tds" "Td’’"
"Term" "Termination"
"Test" "Test"
"Tgt" "Target"
"Thd" " Total harmonic distortion"
"Thm" "Thermal"
"TiF" " Telephone influence factor"
"Tm" "Time"
"Tmh" "Time in h"
"Tmm" "Time in min"
"Tms" "Time in s"
"Tmms" "Time in ms"
"Tmp" "Temperature (°C)"
"Tnk" "Tank"
"To" "Top"
"Tot" "Total"
"TP" "Three pole"
"Tpc" "Teleprotection"
"Tqp" "Tq’"
"Tq0p" "Tq0’"
"Tq0s" "Tq0’’"
"Tqs" "Tq ’’"
"Tr" "Trip"
"Trd" "Trade"
"Trip" "Trip"
"Trg" "Trigger"
"Trk" "Track, tracking"
"Trs" "Transient"
"Ts" "Total signed"
"Tu" "Total unsigned"
"Tx" "Transmit, transmitted"
"Typ" "Type"
"Uhf" "Ultra-high-frequency"
"Un" "Under"
"Up" "Up, upwards"
"V" "Voltage"
"VA" "Volt amperes"
"Va" "Variation"
"Vac" "Vacuum"
"Val" "Value"
"VAr" " Volt amperes reactive"
"Ver" "Vertical"
"Vbr" "Vibration"
"Viol" "Violation"
"Visc" "Viscosity"
"Vlm" "Volume"
"Vlv" "Valve"
"Vol" "Voltage non-phase-related"
"Volts" "Voltage"
"VT" "Voltage transducer"
"W" "Active power"
"Wac" "Watchdog"
"Watt" " Active power non-phase-related"
"Wav" "Wave, waveform"
"Wd" "Wind"
"Week" "Week"
"Wei" " Weak end infeed"
"Wh" "Watt hours"
"Wid" "Width"
"Win" "Window"
"Wrm" "Warm"
"Wrn" "Warning"
"X0" " Zero sequence reactance"
"X1" " Positive sequence reactance"
"X2" " Negative sequence reactance X2"
"Xd" " synchronous reactance Xd"
"Xdp" " transient synchronous reactance Xd’"
"Xds" "Reactance Xd’’"
"Xq" " synchronous reactance Xq"
"Xqp" "transient reactance"
"Xqs" " sub-transient reactance Xq’’"
"Year" "Year"
"Z" "Impedance"
"Z0" " Zero sequence impedance"
"Z1" " Positive sequence impedance"
"Zer" "Zero"
"Zn" "Zone"
"Zro" " Zero sequence method "})

(def abbrev-7-420
  {"Abs" "Absorbing"
"Acc" "Accumulated"
"Act" "Active, activated"
"Algn" "Alignment"
"Alt" "Altitude"
"Amb" "Ambient"
"Arr" "Array"
"Aval" "Available"
"Azi" "Azimuth"
"Bas" "Base"
"Bck" "Backup"
"Bnd" "Band"
"Cal" "Calorie, caloric"
"Cct" "Circuit"
"Cmpl" "Complete, completed"
"Cmut" "Commute, commutator"
"Cnfg" "Configuration"
"Cntt" "Contractual"
"Con" "Constant"
"Conn" "Connected, connections"
"Conv" "Conversion, converted"
"Cool" "Coolant"
"Cost" "Cost"
"Csmp" "Consumption, consumed"
"Day" "Day"
"Db" "Deadband"
"Dc" "Direct current"
"Dct" "Direct"
"DCV" "DC voltage"
"Deg" "Degrees"
"Dep" "Dependent"
"DER" " Distributed energy resource"
"Dff" "Diffuse"
"Drt" "Derate"
"Drv" "Drive"
"ECP" "Electrical connection point"
"Efc" "Efficiency"
"El" "Elevation"
"Em" "Emission"
"Emg" "Emergency"
"Encl" "Enclosure"
"Eng" "Engine"
"Est" "Estimated"
"ExIm" "Export/import"
"Exp" "Export"
"Forc" "Forced"
"Fuel" "Fuel"
"Fx" "Fixed"
"Gov" "Governor"
"Heat" "Heat"
"Hor" "Horizontal"
"Hr" "Hour"
"Hyd" "Hydrogen (suuggested in addition to H2)"
"Id" "Identity"
"ID" "Identity"
"Imp" "Import"
"Ind" "Independent"
"Inert" "Inertia"
"Inf" "Information"
"Insol" "Insolation"
"Isld" "Islanded"
"Iso" "Isolation"
"Maint" "Maintenance"
"Man" "Manual"
"Mat" "Material"
"Mdul" "Module"
"Mgt" "Management"
"Mrk" "Market"
"Obl" "Obligation"
"Off" "Off"
"On" "On"
"Ox" "Oxidant"
"Oxy" "Oxygen"
"Pan" "Panel"
"PCC" " Point of common coupling"
"Perm" "Permission"
"Pk" "Peak"
"Plnt" "Plant, facility"
"Proc" "Process"
"Pv" "Photovoltaics"
"Qud" "Quad"
"Rad" "Radiation"
"Ramp" "Ramp"
"Rdy" "Ready"
"Reg" "Regulation"
"Rng" "Range"
"Rsv" "Reserve"
"Schd" "Schedule"
"Self" "Self"
"Ser" "Series, serial"
"Slp" "Sleep"
"Snw" "Snow"
"Srt" "Short"
"Stab" "Stabilizer"
"Stp" "Step"
"Thrm" "Thermal"
"Tilt" "Tilt"
"Tm" "Time"
"Trk" "Track"
"Tur" "Turbine"
"Unld" "Unload"
"Util" "Utility"
"Vbr" "Vibration"
"Ver" "Vertical"
"Volm" "Volume"
"Wtr" " Water (suggested in addition to H2O)"
"Wup" "Wake up"
"Xsec" "Cross-section"
})

(def abbrev-7-410
  {"Act" "Action, active  "
"Adj" "Adjustment  "
"Alg" "Algorithm  "
"Amb" "Ambient  "
"Ax" "Axial "
"Brg" "Bearing  "
"Brk" "Brake "
"C" "Carbon  "
"Cam" " Cam "
"Cff" "Coefficient  "
"Cm" "Centimeters  "
"Cmpl" " Completed, completion, complete  "
"Cndct" " Conductivity "
"CO" "Carbon monoxide  "
"CO2" "Carbon dioxide  "
"Credit" "Credit "
"Crl" "Correlation  "
"Crp" "Creeping, creepage  "
"Cst" "Constant  "
"Cvr" "Cover "
"D" "Derivative  "
"Dam" "Dam  "
"Defl" " Deflector (used in Pelton turbines)  "
"Dew" "Dew, condensation  "
"Dgr" "Degrees  "
"Dl" " Delay, daylight "
"Dn" " Down, below, downstream, downside  "
"Insol" " Insolation "
"K" "Proportional gain constant "
"Lft" "Left "
"Lkg" "Leakage "
"Lub" " Lubrication "
"Mag" " Magnetic, magnetism "
"Msg" " Message "
"Mvm" " Moving, movement "
"Ndl" " Needle (used in Pelton turbines) "
"NOX" " Nitrogen oxides "
"O2" "Oxygen "
"O3" " Ozone "
"Operate" " Operate order to a device "
"P" "Proportional "
"Pc" "Percent "
"PH" " Acidity  "
"Pt" "Point "
"Rad" " Radiation, radiants "
"Rb" "Runner blade "
"Rect" " Rectifier "
"Res" " Reservoir "
"Rmp" "Ramp "
"Rn" " Rain "
"Rst" "Restraint "
"Sat" "Saturation "
"Slnt" " Salinity, saline content "
"Snd" " Sound, audible noise "
"Dsp" "Displacement  "
"Dust" "Dust, particles suspended in air  "
"Dvc" "Device  "
"Err" "Error "
"Fil" " Filter "
"Fld" " Field (e.g. magnetic field)  "
"Fll" "Fall "
"Flush" " Flush, flushing "
"Green" " Green (e.g. green tag)  "
"Gte" " Gate, dam gate  "
"Gust" " Gust, (e.g. wind gust)  "
"Hd" " Head "
"Hmdt" "Humidity  "
"Hor" "Horizontal  "
"Hyd" " Hydrological, hydro, water  "
"I" "Integral  "
"Inert" "Inertia "
"Snw" " Snow "
"SOX" " Sulphur oxides "
"Spt" "Process set-point "
"Srfc" " Surface "
"Stat" " Stator (also statistics) "
"Stl" " Still, not moving "
"Stnd" " Stand, standing "
"Stuck" " Cannot move "
"Tnk" " Tank "
"Tns" " Tension, mechanical stress "
"Trade" " Trade "
"Up" " Up, above, upstream, upside "
"Vbr" "Vibration "
"Ver" "Vertical "
"Vlm" " Volume "
"Wd" " Wind "
"Wet" "Wet "
})