# Hybrid Vehicle Attribution for Shared EV Chargers

**Charging Sessions** store an optional `carId` so per-vehicle charger exports can be assigned directly to a specific EV while residential chargers shared by multiple EVs can be imported with `carId = null` (**Shared Charger Usage**). During insights calculation for a given time interval, unassigned Home Charging kWh is attributed only across active EVs (`isEv == true`) in proportion to their telemetry miles driven in that interval, falling back to an equal split among active EVs when total EV miles in the interval is zero.
