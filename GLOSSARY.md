# Garage Journal (Car Logger)

A personal vehicle management and telemetry insights context for tracking maintenance, tire lifecycles, driving activity, and EV charging economics across a household garage.

## Language

### EV Charging & Utility Analysis

**Utility Bill**:
A periodic statement from an electric utility provider covering a specific **Billing Period**, recording total household electricity consumption (kWh), **Volumetric Charges**, and **Fixed Monthly Charges**.
_Avoid_: Electric receipt, power invoice

**Billing Period**:
The inclusive start and end date window covered by a **Utility Bill**.
_Avoid_: Statement cycle, meter window

**Fixed Monthly Charge**:
Non-volumetric utility fees billed per statement regardless of electricity consumption (such as customer charges, basic service fees, or grid connection fees).
_Avoid_: Base tax, standing fee

**Volumetric Charge**:
The portion of a **Utility Bill** that scales with electricity usage (Total Bill Amount minus Fixed Monthly Charges), including generation/supply, delivery/transmission, and per-kWh riders or taxes.
_Avoid_: Usage fee, variable bill

**Effective Rate**:
The per-kilowatt-hour cost of home electricity ($/kWh) derived from a **Utility Bill** as the Variable Blended Rate (Volumetric Charges ÷ Total Billed kWh), or a user-specified manual override.
_Avoid_: Blended rate, tariff rate, unit price

**Charging Session**:
A discrete EV charging event with a date/timestamp, energy dispensed (kWh), a charging venue (**Home Charging** or **Public Charging**), and an optional vehicle assignment.
_Avoid_: Plug-in event, charge log, fill-up

**Charger Usage Summary**:
An aggregated record of total energy dispensed (kWh) over a multi-day date range when per-session timestamps are unavailable, prorated evenly across the days in its window.
_Avoid_: Bulk usage, monthly charger total

**Home Charging**:
Electricity dispensed at a residential charger whose cost is calculated by multiplying session kWh by the applicable **Utility Bill** **Effective Rate** (or the fallback rate when no bill covers the date).
_Avoid_: Level 2 charging, domestic charging

**Public Charging**:
Electricity dispensed at a commercial or DC fast charger (e.g., Supercharger, Electrify America) billed directly per session rather than through a residential **Utility Bill**.
_Avoid_: External charging, away charging

**Shared Charger Usage**:
Unassigned **Home Charging** energy (kWh) from a charger shared by multiple EVs in the garage, attributed across active EVs in proportion to their telemetry miles driven during the interval (or split equally when interval miles are zero).
_Avoid_: Unallocated kWh, fleet pool

### Comparative Insights

**Gas Equivalent Cost**:
The estimated fuel cost if the telemetry miles driven by an EV during an observation window had instead been driven in a baseline gasoline vehicle (Miles ÷ Baseline MPG × Gas Price).
_Avoid_: Avoided gas cost, hypothetical fuel spend

**Net Fuel Savings**:
The net financial savings from driving electric during an observation window, defined as Gas Equivalent Cost minus (Home Charging Cost + Public Charging Cost).
_Avoid_: Gross gas savings, EV profit
