import 'dart:io' show File;
import 'dart:ui' show ImageFilter;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../data/vehicle_data.dart';
import '../models/car.dart';
import '../models/maintenance_record.dart';
import '../widgets/add_car_modal.dart';
import '../widgets/maintenance_record_dialog.dart';
import 'car_details_page.dart';
import '../services/storage_service.dart';
import '../widgets/material3_body_container.dart';
import '../widgets/raised_navigation_bar.dart';
import 'google_drive_sync_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<Car> _cars = [];
  String _garageName = StorageService.defaultGarageName;
  int _currentTab = 0;
  bool _isSpeedDialOpen = false;
  final Set<String> _dismissedWarningCarIds = {};

  bool get _hasUrgentService {
    for (final car in _cars) {
      if (car.isArchived || car.odometer == null) continue;
      final records = StorageService.getMaintenanceRecords(car.id);
      final latestRecordOdo = records.isNotEmpty
          ? records.map((r) => r.odometer).reduce((a, b) => a > b ? a : b)
          : car.odometer!;
      final serviceInterval = car.isEv ? 7500 : 5000;
      final nextServiceOdo = latestRecordOdo + serviceInterval;
      final remainingMiles = nextServiceOdo - car.odometer!;
      if (remainingMiles <= 500) {
        return true;
      }
    }
    return false;
  }

  @override
  void initState() {
    super.initState();
    _loadGarageName();
    _loadCars();
  }

  void _loadGarageName() {
    setState(() {
      _garageName = StorageService.getGarageName();
    });
  }



  void _loadCars() {
    setState(() {
      final cars = StorageService.getCars();
      // Active cars first, archived cars at the bottom
      cars.sort((a, b) {
        if (a.isArchived == b.isArchived) return 0;
        return a.isArchived ? 1 : -1;
      });
      _cars = cars;
    });
  }

  void _toggleSpeedDial() {
    setState(() {
      _isSpeedDialOpen = !_isSpeedDialOpen;
    });
  }

  void _closeSpeedDial() {
    if (_isSpeedDialOpen) {
      setState(() {
        _isSpeedDialOpen = false;
      });
    }
  }

  Future<Car?> _selectCar(BuildContext context, String title) async {
    if (_cars.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please add a vehicle first.')),
      );
      return null;
    }
    if (_cars.length == 1) {
      return _cars[0];
    }
    return await showModalBottomSheet<Car>(
      context: context,
      builder: (context) {
        return SafeArea(
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Text(
                    title,
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                ),
                const Divider(),
                ..._cars.map((car) {
                  return ListTile(
                    leading: Icon(
                      Icons.directions_car,
                      color: car.isArchived ? Colors.grey : null,
                    ),
                    title: Text(
                      '${car.year} ${car.make} ${car.model}${car.isArchived ? ' (Archived)' : ''}',
                      style: TextStyle(
                        color: car.isArchived ? Colors.grey : null,
                      ),
                    ),
                    subtitle: car.licensePlate != null ? Text(car.licensePlate!) : null,
                    onTap: () => Navigator.pop(context, car),
                  );
                }),
              ],
            ),
          ),
        );
      },
    );
  }

  Future<void> _addCarFlow() async {
    _closeSpeedDial();
    final newCar = await showModalBottomSheet<Car>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const AddCarModal(),
    );

    if (newCar != null) {
      await StorageService.addCar(newCar);
      _loadCars();

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Added ${newCar.make} ${newCar.model}'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  Future<void> _addCarByVinFlow() async {
    _closeSpeedDial();
    final newCar = await showModalBottomSheet<Car>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const AddCarModal(openVinPrompt: true),
    );

    if (newCar != null) {
      await StorageService.addCar(newCar);
      _loadCars();

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Added ${newCar.make} ${newCar.model}'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  Future<void> _deleteCarFlow(Car car) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.delete_outline, color: Colors.red),
        title: const Text('Remove Vehicle'),
        content: Text(
          'Are you sure you want to remove your ${car.year} ${car.make} ${car.model}? '
          'This will also delete all associated maintenance records.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(context).colorScheme.error,
            ),
            child: const Text('Remove'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await StorageService.deleteCar(car.id);
      _loadCars();

      if (!mounted) return;

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Removed ${car.make} ${car.model}'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  Future<void> _toggleArchiveCar(Car car) async {
    final newArchived = !car.isArchived;
    await StorageService.toggleArchiveCar(car.id);
    _loadCars();

    if (!mounted) return;
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          newArchived
              ? 'Archived ${car.make} ${car.model}'
              : 'Unarchived ${car.make} ${car.model}',
        ),
        action: SnackBarAction(
          label: 'Undo',
          onPressed: () async {
            await StorageService.toggleArchiveCar(car.id);
            _loadCars();
          },
        ),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  Future<void> _showCarOptionsSheet(Car car) async {
    await showModalBottomSheet(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: Icon(car.isArchived ? Icons.unarchive_outlined : Icons.archive_outlined),
              title: Text(car.isArchived ? 'Unarchive Vehicle' : 'Archive Vehicle'),
              onTap: () {
                Navigator.pop(context);
                _toggleArchiveCar(car);
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete_outline, color: Colors.red),
              title: const Text('Remove Vehicle', style: TextStyle(color: Colors.red)),
              onTap: () {
                Navigator.pop(context);
                _deleteCarFlow(car);
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _addMaintenanceRecordForCar(Car car) async {
    _closeSpeedDial();
    final result = await showMaintenanceRecordDialog(
      context,
      car: car,
      onCarUpdated: (updatedCar) {
        _loadCars();
      },
    );

    if (result != null && result.action == MaintenanceRecordAction.saved && result.record != null) {
      _loadCars();

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Added maintenance record: ${result.record!.title}')),
      );
    }
  }

  Future<void> _addMaintenanceRecordFlow() async {
    _closeSpeedDial();
    final car = await _selectCar(context, 'Select Vehicle for Maintenance');
    if (car == null) return;
    if (!mounted) return;
    await _addMaintenanceRecordForCar(car);
  }

  Future<void> _updateOdometerFlowForCar(Car car) async {
    final controller = TextEditingController(
      text: car.odometer != null ? car.odometer.toString() : '',
    );

    final newOdometer = await showDialog<int>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text('Update Odometer for ${car.make}'),
          content: TextField(
            controller: controller,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: 'Odometer (miles)',
              hintText: 'e.g. 45000',
            ),
            autofocus: true,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                final value = int.tryParse(controller.text);
                if (value != null && value >= 0) {
                  Navigator.pop(context, value);
                }
              },
              child: const Text('Save'),
            ),
          ],
        );
      },
    );

    if (newOdometer != null && newOdometer != car.odometer) {
      final updatedCar = car.copyWith(odometer: newOdometer);
      final cars = StorageService.getCars();
      final index = cars.indexWhere((c) => c.id == car.id);
      if (index != -1) {
        cars[index] = updatedCar;
        await StorageService.saveCars(cars);
        _loadCars();
      }
    }
  }

  Future<void> _updateOdometerFlow() async {
    _closeSpeedDial();
    final car = await _selectCar(context, 'Select Vehicle to Update Odometer');
    if (car == null) return;
    await _updateOdometerFlowForCar(car);
  }

  Future<void> _renameGarageFlow() async {
    final controller = TextEditingController(text: _garageName);
    final formKey = GlobalKey<FormState>();

    final newName = await showDialog<String>(
      context: context,
      builder: (dialogContext) {
        return AlertDialog(
          title: const Text('Rename Garage'),
          content: Form(
            key: formKey,
            child: TextFormField(
              controller: controller,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Garage Name',
                hintText: 'e.g. My Garage',
                border: OutlineInputBorder(),
              ),
              textCapitalization: TextCapitalization.words,
              validator: (value) {
                if (value == null || value.trim().isEmpty) {
                  return 'Please enter a garage name';
                }
                return null;
              },
              onFieldSubmitted: (value) {
                if (formKey.currentState?.validate() ?? false) {
                  Navigator.of(dialogContext).pop(value.trim());
                }
              },
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                if (formKey.currentState?.validate() ?? false) {
                  Navigator.of(dialogContext).pop(controller.text.trim());
                }
              },
              child: const Text('Save'),
            ),
          ],
        );
      },
    );

    if (newName != null && newName.isNotEmpty && newName != _garageName) {
      await StorageService.saveGarageName(newName);
      setState(() {
        _garageName = newName;
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Garage renamed to "$newName"')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Material3BodyContainer.getShellColor(context),
      body: Stack(
        children: [
          IndexedStack(
            index: _currentTab,
            children: [
              _buildGarageTab(context),
              _buildActivityTab(context),
              _buildScheduleTab(context),
              _buildSettingsTab(context),
            ],
          ),

          // Speed Dial Backdrop Overlay
          if (_isSpeedDialOpen)
            GestureDetector(
              onTap: _closeSpeedDial,
              child: AnimatedOpacity(
                duration: const Duration(milliseconds: 250),
                opacity: _isSpeedDialOpen ? 0.6 : 0.0,
                child: Container(
                  color: Colors.black87,
                ),
              ),
            ),

          // Speed Dial Options Column
          if (_isSpeedDialOpen)
            Positioned(
              right: 16,
              bottom: 80,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  _buildSpeedDialItem(
                    label: 'Add Vehicle',
                    icon: Icons.directions_car,
                    onTap: _addCarFlow,
                  ),
                  const SizedBox(height: 12),
                  _buildSpeedDialItem(
                    label: 'Add by VIN',
                    icon: Icons.pin_outlined,
                    onTap: _addCarByVinFlow,
                  ),
                  const SizedBox(height: 12),
                  _buildSpeedDialItem(
                    label: 'Add Maintenance',
                    icon: Icons.build,
                    onTap: _addMaintenanceRecordFlow,
                  ),
                  const SizedBox(height: 12),
                  _buildSpeedDialItem(
                    label: 'Update Odometer',
                    icon: Icons.speed,
                    onTap: _updateOdometerFlow,
                  ),
                ],
              ),
            ),
        ],
      ),
      bottomNavigationBar: RaisedNavigationBar(
        selectedIndex: _currentTab,
        onDestinationSelected: (index) {
          _closeSpeedDial();
          setState(() {
            _currentTab = index;
          });
        },
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.garage_outlined),
            selectedIcon: Icon(Icons.garage),
            label: 'Garage',
          ),
          const NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long),
            label: 'Activity',
          ),
          NavigationDestination(
            icon: Badge(
              isLabelVisible: _hasUrgentService,
              backgroundColor: Colors.amber,
              smallSize: 8,
              child: const Icon(Icons.calendar_month_outlined),
            ),
            selectedIcon: Badge(
              isLabelVisible: _hasUrgentService,
              backgroundColor: Colors.amber,
              smallSize: 8,
              child: const Icon(Icons.calendar_month),
            ),
            label: 'Schedule',
          ),
          const NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: 'Settings',
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _toggleSpeedDial,
        child: AnimatedRotation(
          turns: _isSpeedDialOpen ? 0.125 : 0.0,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
          child: const Icon(Icons.add),
        ),
      ),
    );
  }

  Widget _buildSpeedDialItem({
    required String label,
    required IconData icon,
    required VoidCallback onTap,
  }) {
    final theme = Theme.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            borderRadius: BorderRadius.circular(8),
            boxShadow: const [
              BoxShadow(
                color: Colors.black26,
                blurRadius: 4,
                offset: Offset(0, 2),
              ),
            ],
          ),
          child: Text(
            label,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontWeight: FontWeight.bold,
              color: theme.colorScheme.onSurface,
            ),
          ),
        ),
        const SizedBox(width: 12),
        FloatingActionButton.small(
          onPressed: onTap,
          heroTag: label,
          child: Icon(icon),
        ),
      ],
    ).animate().fadeIn(duration: 200.ms).slideY(begin: 0.3, end: 0, curve: Curves.easeOut);
  }

  Widget _buildEnvelopedTab({
    required BuildContext context,
    required Widget title,
    Widget? subtitle,
    List<Widget>? actions,
    required Widget body,
  }) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      backgroundColor: Material3BodyContainer.getShellColor(context),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Seamless Frameless Header (no divider line, no drop shadow, M3 padding)
          SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 12, 18),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        DefaultTextStyle(
                          style: theme.textTheme.headlineSmall!.copyWith(
                            fontWeight: FontWeight.bold,
                            color: colorScheme.onSurface,
                          ),
                          child: title,
                        ),
                        if (subtitle != null) ...[
                          const SizedBox(height: 4),
                          DefaultTextStyle(
                            style: theme.textTheme.bodySmall!.copyWith(
                              color: colorScheme.onSurfaceVariant,
                            ),
                            child: subtitle,
                          ),
                        ],
                      ],
                    ),
                  ),
                  if (actions != null && actions.isNotEmpty)
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: actions,
                    ),
                ],
              ),
            ),
          ),

          // Enveloped Body Canvas (no drag handle, zero edge bleeding, solid surface transition)
          Expanded(
            child: Material3BodyContainer(
              child: body,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGarageTab(BuildContext context) {
    final vehicleCountText = _cars.isEmpty
        ? 'No vehicles yet'
        : '${_cars.length} ${_cars.length == 1 ? 'vehicle' : 'vehicles'}';

    return _buildEnvelopedTab(
      context: context,
      title: Text(_garageName),
      subtitle: Text(vehicleCountText),
      actions: [
        IconButton(
          icon: const Icon(Icons.edit_outlined),
          tooltip: 'Rename Garage',
          onPressed: _renameGarageFlow,
        ),
        IconButton(
          icon: const Icon(Icons.cloud_sync),
          tooltip: 'Sync Google Drive',
          onPressed: () {
            Navigator.push(
              context,
              MaterialPageRoute(
                builder: (context) => const GoogleDriveSyncPage(isTab: false),
              ),
            );
          },
        ),
      ],
      body: _cars.isEmpty
          ? const Center(child: Text('No vehicles added yet.'))
          : ListView(
              padding: const EdgeInsets.fromLTRB(16.0, 20.0, 16.0, 80.0),
              children: [
                _buildUrgentServiceWarning(context),
                ..._cars.map((car) => _buildVehicleCard(context, car)),
              ],
            ),
    );
  }

  Widget _buildUrgentServiceWarning(BuildContext context) {
    final theme = Theme.of(context);

    // Find the most urgent car needing service that hasn't been dismissed
    Car? urgentCar;
    int lowestRemainingMiles = 999999;
    String serviceName = '';

    for (final car in _cars) {
      if (car.isArchived || car.odometer == null || _dismissedWarningCarIds.contains(car.id)) continue;
      final records = StorageService.getMaintenanceRecords(car.id);
      final latestRecordOdo = records.isNotEmpty
          ? records.map((r) => r.odometer).reduce((a, b) => a > b ? a : b)
          : car.odometer!;
      final serviceInterval = car.isEv ? 7500 : 5000;
      final nextServiceOdo = latestRecordOdo + serviceInterval;
      final remainingMiles = nextServiceOdo - car.odometer!;

      if (remainingMiles <= 500 && remainingMiles < lowestRemainingMiles) {
        lowestRemainingMiles = remainingMiles;
        urgentCar = car;
        serviceName = car.isEv ? 'Next Tire Rotation' : 'Next Oil Change';
      }
    }

    if (urgentCar == null) {
      return const SizedBox.shrink();
    }

    final isOverdue = lowestRemainingMiles <= 0;
    final warningColor = isOverdue ? theme.colorScheme.error : Colors.amber.shade700;
    final containerColor = isOverdue
        ? theme.colorScheme.errorContainer.withValues(alpha: 0.3)
        : Colors.amber.withValues(alpha: 0.12);
    final borderColor = isOverdue
        ? theme.colorScheme.error.withValues(alpha: 0.5)
        : Colors.amber.withValues(alpha: 0.4);

    return Card(
      margin: const EdgeInsets.only(bottom: 16.0),
      color: containerColor,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: borderColor, width: 1),
      ),
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: warningColor.withValues(alpha: 0.18),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: warningColor.withValues(alpha: 0.3)),
              ),
              child: Icon(Icons.warning_amber_rounded, color: warningColor, size: 22),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        isOverdue ? 'SERVICE DUE NOW' : 'SERVICE DUE SOON',
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: warningColor,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 0.5,
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                          color: warningColor.withValues(alpha: 0.18),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: warningColor.withValues(alpha: 0.3)),
                        ),
                        child: Text(
                          isOverdue ? 'Overdue' : 'In $lowestRemainingMiles mi',
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            color: warningColor,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${urgentCar.year} ${urgentCar.make} ${urgentCar.model}',
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '$serviceName due for this vehicle.',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      FilledButton.tonalIcon(
                        style: FilledButton.styleFrom(
                          visualDensity: VisualDensity.compact,
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        ),
                        icon: const Icon(Icons.build, size: 15),
                        label: const Text('Log Service', style: TextStyle(fontSize: 12)),
                        onPressed: () => _addMaintenanceRecordForCar(urgentCar!),
                      ),
                      const SizedBox(width: 8),
                      TextButton(
                        style: TextButton.styleFrom(
                          visualDensity: VisualDensity.compact,
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                        ),
                        onPressed: () {
                          setState(() {
                            _dismissedWarningCarIds.add(urgentCar!.id);
                          });
                        },
                        child: Text(
                          'Dismiss',
                          style: TextStyle(
                            fontSize: 12,
                            color: theme.colorScheme.outline,
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildVehicleCard(BuildContext context, Car car) {
    final theme = Theme.of(context);
    final records = StorageService.getMaintenanceRecords(car.id);
    final carTotalSpent = records.fold<double>(0.0, (sum, r) => sum + (r.cost ?? 0.0));

    // Next Service calculations (7.5k tire rotation interval for EV, 5k oil change for gas)
    final latestRecordOdo = records.isNotEmpty
        ? records.map((r) => r.odometer).reduce((a, b) => a > b ? a : b)
        : (car.odometer ?? 0);
    final serviceInterval = car.isEv ? 7500 : 5000;
    final nextServiceOdo = latestRecordOdo + serviceInterval;
    final remainingMiles = car.odometer != null ? nextServiceOdo - car.odometer! : serviceInterval;

    final cardWidget = Card(
      margin: const EdgeInsets.only(bottom: 16.0),
      elevation: 0,
      color: Colors.black,
      shadowColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20.0),
        side: BorderSide(
          color: car.isArchived
              ? Colors.white.withValues(alpha: 0.08)
              : Colors.white.withValues(alpha: 0.15),
          width: 1.2,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () async {
          await Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => CarDetailsPage(
                car: car,
                onCarUpdated: (updatedCar) {
                  _loadCars();
                },
              ),
            ),
          );
          _loadCars();
        },
        onLongPress: () => _showCarOptionsSheet(car),
        borderRadius: BorderRadius.circular(20.0),
        child: SizedBox(
          height: 225,
          width: double.infinity,
          child: Stack(
            children: [
              // Background Car Image
              Positioned.fill(
                child: _buildDigitalKeyCarImage(car, theme),
              ),
              // Cinematic Gradient Overlay (darker at top & bottom, clear in middle)
              Positioned.fill(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Colors.black.withValues(alpha: 0.75),
                        Colors.black.withValues(alpha: 0.20),
                        Colors.black.withValues(alpha: 0.88),
                      ],
                      stops: const [0.0, 0.40, 1.0],
                    ),
                  ),
                ),
              ),
              // Content Stack
              Padding(
                padding: const EdgeInsets.all(14.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    // Top Row: Active/Archived Tag, Title, Plate, Chevron & Options
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2.5),
                                decoration: BoxDecoration(
                                  color: car.isArchived
                                      ? Colors.white.withValues(alpha: 0.10)
                                      : Colors.white.withValues(alpha: 0.18),
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(
                                    color: car.isArchived ? Colors.white12 : Colors.white24,
                                    width: 0.5,
                                  ),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Container(
                                      width: 6,
                                      height: 6,
                                      decoration: BoxDecoration(
                                        color: car.isArchived ? Colors.grey : Colors.greenAccent,
                                        shape: BoxShape.circle,
                                      ),
                                    ),
                                    const SizedBox(width: 4),
                                    Text(
                                      car.isArchived
                                          ? (car.isEv ? 'ARCHIVED EV' : 'ARCHIVED')
                                          : (car.isEv ? 'ACTIVE EV' : 'ACTIVE'),
                                      style: TextStyle(
                                        fontSize: 9,
                                        fontWeight: FontWeight.bold,
                                        letterSpacing: 0.5,
                                        color: car.isArchived ? Colors.white70 : Colors.white,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 5),
                              Text(
                                '${car.year} ${car.make} ${car.model}',
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: Colors.white,
                                  shadows: const [
                                    Shadow(color: Colors.black87, blurRadius: 4, offset: Offset(0, 1)),
                                  ],
                                ),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              if (car.licensePlate != null && car.licensePlate!.isNotEmpty)
                                Text(
                                  car.licensePlate!,
                                  style: const TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                    letterSpacing: 1.0,
                                    color: Colors.white70,
                                  ),
                                ),
                            ],
                          ),
                        ),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            PopupMenuButton<String>(
                              icon: Container(
                                padding: const EdgeInsets.all(6),
                                decoration: BoxDecoration(
                                  color: Colors.white.withValues(alpha: 0.18),
                                  shape: BoxShape.circle,
                                  border: Border.all(color: Colors.white24),
                                ),
                                child: const Icon(Icons.more_vert, size: 14, color: Colors.white),
                              ),
                              tooltip: 'Vehicle options',
                              padding: EdgeInsets.zero,
                              constraints: const BoxConstraints(),
                              onSelected: (value) {
                                if (value == 'archive') {
                                  _toggleArchiveCar(car);
                                } else if (value == 'delete') {
                                  _deleteCarFlow(car);
                                }
                              },
                              itemBuilder: (context) => [
                                PopupMenuItem<String>(
                                  value: 'archive',
                                  child: Row(
                                    children: [
                                      Icon(
                                        car.isArchived ? Icons.unarchive_outlined : Icons.archive_outlined,
                                        size: 20,
                                      ),
                                      const SizedBox(width: 8),
                                      Text(car.isArchived ? 'Unarchive Vehicle' : 'Archive Vehicle'),
                                    ],
                                  ),
                                ),
                                PopupMenuItem<String>(
                                  value: 'delete',
                                  child: Row(
                                    children: [
                                      const Icon(Icons.delete_outline, color: Colors.red, size: 20),
                                      const SizedBox(width: 8),
                                      Text(
                                        'Remove Vehicle',
                                        style: TextStyle(color: theme.colorScheme.error),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(width: 6),
                            Container(
                              padding: const EdgeInsets.all(6),
                              decoration: BoxDecoration(
                                color: Colors.white.withValues(alpha: 0.18),
                                shape: BoxShape.circle,
                                border: Border.all(color: Colors.white24),
                              ),
                              child: const Icon(Icons.arrow_forward_ios, size: 12, color: Colors.white),
                            ),
                          ],
                        ),
                      ],
                    ),

                    // Bottom HUD Shelf (frosted glass blur)
                    // Bottom HUD Shelf (pure minimalist 3-column)
                    ClipRRect(
                      borderRadius: BorderRadius.circular(16.0),
                      child: BackdropFilter(
                        filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 14.0, vertical: 12.0),
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.55),
                            borderRadius: BorderRadius.circular(16.0),
                            border: Border.all(
                              color: Colors.white.withValues(alpha: 0.18),
                              width: 1,
                            ),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceAround,
                            children: [
                              // Odometer
                              _buildDigitalKeyMetric(
                                label: 'Odometer',
                                value: car.odometer != null
                                    ? '${NumberFormat.decimalPattern().format(car.odometer)} mi'
                                    : 'Not set',
                                color: Colors.white,
                              ),
                              Container(width: 1, height: 26, color: Colors.white12),
                              // Next Service
                              _buildDigitalKeyMetric(
                                label: car.isEv ? 'Tire Rotation' : 'Next Service',
                                value: remainingMiles > 0
                                    ? 'In ${NumberFormat.decimalPattern().format(remainingMiles)} mi'
                                    : 'Due Now',
                                color: remainingMiles > (car.isEv ? 1500 : 1000)
                                    ? Colors.white
                                    : (remainingMiles > 0 ? Colors.amberAccent : Colors.redAccent),
                              ),
                              Container(width: 1, height: 26, color: Colors.white12),
                              // Total Spent
                              _buildDigitalKeyMetric(
                                label: 'Total Spent',
                                value: NumberFormat.simpleCurrency().format(carTotalSpent),
                                color: Colors.greenAccent,
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );

    if (car.isArchived) {
      return Opacity(
        opacity: 0.6,
        child: ColorFiltered(
          colorFilter: const ColorFilter.mode(
            Colors.grey,
            BlendMode.saturation,
          ),
          child: cardWidget,
        ),
      );
    }

    return cardWidget;
  }

  Widget _buildActivityTab(BuildContext context) {
    final theme = Theme.of(context);
    final List<MapEntry<Car, MaintenanceRecord>> allRecords = [];
    for (final car in _cars) {
      final records = StorageService.getMaintenanceRecords(car.id);
      for (final r in records) {
        allRecords.add(MapEntry(car, r));
      }
    }

    // Sort by date descending
    allRecords.sort((a, b) => b.value.date.compareTo(a.value.date));

    final countText = allRecords.isEmpty
        ? 'No records yet'
        : '${allRecords.length} ${allRecords.length == 1 ? 'record' : 'records'} logged';

    return _buildEnvelopedTab(
      context: context,
      title: const Text('Activity'),
      subtitle: Text(countText),
      actions: [
        IconButton(
          icon: const Icon(Icons.add),
          tooltip: 'Add Record',
          onPressed: _addMaintenanceRecordFlow,
        ),
      ],
      body: allRecords.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.receipt_long_outlined, size: 64, color: theme.colorScheme.outline),
                  const SizedBox(height: 16),
                  Text(
                    'No maintenance records yet',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 8),
                  FilledButton.icon(
                    icon: const Icon(Icons.add),
                    label: const Text('Add First Record'),
                    onPressed: _addMaintenanceRecordFlow,
                  ),
                ],
              ),
            )
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16.0, 20.0, 16.0, 80.0),
              itemCount: allRecords.length,
              separatorBuilder: (context, index) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final entry = allRecords[index];
                final car = entry.key;
                final record = entry.value;

                return Card(
                  elevation: 0,
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                    leading: CircleAvatar(
                      backgroundColor: theme.colorScheme.primaryContainer,
                      child: Icon(
                        Icons.build,
                        size: 20,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                    title: Text(
                      record.title,
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    subtitle: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 2),
                        Text('${car.year} ${car.make} ${car.model}'),
                        const SizedBox(height: 2),
                        Text(
                          '${DateFormat.yMMMd().format(record.date)} • ${NumberFormat.decimalPattern().format(record.odometer)} mi',
                          style: TextStyle(
                            fontSize: 12,
                            color: theme.colorScheme.outline,
                          ),
                        ),
                      ],
                    ),
                    trailing: record.cost != null
                        ? Text(
                            NumberFormat.simpleCurrency().format(record.cost),
                            style: const TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 15,
                              color: Colors.green,
                            ),
                          )
                        : null,
                    onTap: () async {
                      final result = await showMaintenanceRecordDialog(
                        context,
                        car: car,
                        record: record,
                        onCarUpdated: (_) => _loadCars(),
                      );
                      if (result != null) {
                        _loadCars();
                      }
                    },
                  ),
                );
              },
            ),
    );
  }

  Widget _buildScheduleTab(BuildContext context) {
    final theme = Theme.of(context);
    final List<Map<String, dynamic>> schedules = [];

    for (final car in _cars) {
      final records = StorageService.getMaintenanceRecords(car.id);
      final latestRecordOdo = records.isNotEmpty
          ? records.map((r) => r.odometer).reduce((a, b) => a > b ? a : b)
          : (car.odometer ?? 0);
      final serviceInterval = car.isEv ? 7500 : 5000;
      final nextServiceOdo = latestRecordOdo + serviceInterval;
      final remainingMiles = car.odometer != null ? nextServiceOdo - car.odometer! : serviceInterval;
      final serviceName = car.isEv ? 'Tire Rotation' : 'Engine Oil & Filter Change';

      schedules.add({
        'car': car,
        'serviceName': serviceName,
        'currentOdo': car.odometer,
        'targetOdo': nextServiceOdo,
        'remainingMiles': remainingMiles,
        'isUrgent': remainingMiles <= 500,
        'isOverdue': remainingMiles <= 0,
      });
    }

    schedules.sort((a, b) => (a['remainingMiles'] as int).compareTo(b['remainingMiles'] as int));

    final upcomingCount = schedules.where((s) => s['isUrgent'] == true).length;
    final subtitleText = upcomingCount > 0
        ? '$upcomingCount ${upcomingCount == 1 ? 'service' : 'services'} due soon'
        : 'Maintenance intervals';

    return _buildEnvelopedTab(
      context: context,
      title: const Text('Schedule'),
      subtitle: Text(subtitleText),
      body: _cars.isEmpty
          ? const Center(child: Text('No vehicles added yet.'))
          : ListView.separated(
              padding: const EdgeInsets.fromLTRB(16.0, 20.0, 16.0, 80.0),
              itemCount: schedules.length,
              separatorBuilder: (context, index) => const SizedBox(height: 12),
              itemBuilder: (context, index) {
                final item = schedules[index];
                final car = item['car'] as Car;
                final serviceName = item['serviceName'] as String;
                final remainingMiles = item['remainingMiles'] as int;
                final isUrgent = item['isUrgent'] as bool;
                final isOverdue = item['isOverdue'] as bool;

                Color statusColor = Colors.green;
                if (isOverdue) {
                  statusColor = theme.colorScheme.error;
                } else if (isUrgent) {
                  statusColor = Colors.amber.shade700;
                }

                return Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                    side: BorderSide(
                      color: isUrgent ? statusColor.withValues(alpha: 0.6) : Colors.transparent,
                      width: isUrgent ? 1.5 : 0,
                    ),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              width: 44,
                              height: 44,
                              decoration: BoxDecoration(
                                color: statusColor.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: Icon(
                                isUrgent ? Icons.warning_amber_rounded : Icons.calendar_month,
                                color: statusColor,
                                size: 24,
                              ),
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    serviceName,
                                    style: theme.textTheme.titleMedium?.copyWith(
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${car.year} ${car.make} ${car.model}',
                                    style: TextStyle(
                                      fontSize: 13,
                                      color: theme.colorScheme.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                              decoration: BoxDecoration(
                                color: statusColor.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: statusColor.withValues(alpha: 0.3)),
                              ),
                              child: Text(
                                isOverdue
                                    ? 'Overdue'
                                    : remainingMiles > 0
                                        ? '${NumberFormat.decimalPattern().format(remainingMiles)} mi'
                                        : 'Due Now',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.bold,
                                  color: statusColor,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 14),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'Target: ${NumberFormat.decimalPattern().format(item['targetOdo'])} mi',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.outline,
                              ),
                            ),
                            if (car.odometer != null)
                              Text(
                                'Current: ${NumberFormat.decimalPattern().format(car.odometer)} mi',
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.outline,
                                ),
                              ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: FilledButton.tonalIcon(
                            icon: const Icon(Icons.build, size: 16),
                            label: const Text('Log Maintenance'),
                            onPressed: () => _addMaintenanceRecordForCar(car),
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }

  Widget _buildSettingsTab(BuildContext context) {
    final theme = Theme.of(context);
    final syncFolderName = StorageService.getSyncFolderName();

    return _buildEnvelopedTab(
      context: context,
      title: const Text('Settings'),
      subtitle: const Text('Preferences & sync'),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16.0, 20.0, 16.0, 80.0),
        children: [
          // Google Drive Sync Card
          Card(
            color: theme.colorScheme.primaryContainer.withValues(alpha: 0.3),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: BorderSide(color: theme.colorScheme.primary.withValues(alpha: 0.3)),
            ),
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: theme.colorScheme.primaryContainer,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(Icons.cloud_sync, color: theme.colorScheme.primary),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Google Drive Sync',
                              style: theme.textTheme.titleMedium?.copyWith(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            Text(
                              syncFolderName != null
                                  ? 'Folder: $syncFolderName'
                                  : 'Sync maintenance logs & receipts',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton(
                          onPressed: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const GoogleDriveSyncPage(isTab: false),
                              ),
                            );
                          },
                          child: const Text('Open Drive Sync'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Preferences section
          Text(
            'Preferences',
            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.garage_outlined),
                  title: const Text('Garage Name'),
                  subtitle: Text('Current: $_garageName'),
                  trailing: const Icon(Icons.edit_outlined),
                  onTap: _renameGarageFlow,
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.straighten),
                  title: const Text('Distance Units'),
                  trailing: Text(
                    'Miles (mi)',
                    style: TextStyle(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.attach_money),
                  title: const Text('Currency'),
                  trailing: Text(
                    'USD (\$)',
                    style: TextStyle(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),

          // About section
          Text(
            'About',
            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Card(
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.info_outline),
                  title: const Text('Garage Journal'),
                  subtitle: const Text('Vehicle & Maintenance Tracker'),
                  trailing: const Text(
                    'v1.0.0',
                    style: TextStyle(color: Colors.grey),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDigitalKeyCarImage(Car car, ThemeData theme) {
    ImageProvider? provider;
    if (car.localImagePath != null && car.localImagePath!.isNotEmpty) {
      if (kIsWeb) {
        provider = NetworkImage(car.localImagePath!);
      } else {
        provider = FileImage(File(car.localImagePath!));
      }
    } else {
      final url = car.imageUrl ?? getDefaultImageUrl(car.make, car.model);
      if (url != null && url.isNotEmpty) {
        provider = NetworkImage(sanitizeImageUrl(url));
      }
    }

    if (provider == null) {
      return Container(
        color: Colors.blueGrey.shade900,
        child: const Center(
          child: Icon(Icons.directions_car, size: 64, color: Colors.white24),
        ),
      );
    }

    return Image(
      image: provider,
      fit: BoxFit.cover,
      errorBuilder: (context, error, stackTrace) => Container(
        color: Colors.blueGrey.shade900,
        child: const Center(
          child: Icon(Icons.directions_car, size: 64, color: Colors.white24),
        ),
      ),
    );
  }

  Widget _buildDigitalKeyMetric({
    required String label,
    required String value,
    required Color color,
  }) {
    return Column(
      children: [
        Text(
          label,
          style: const TextStyle(
            fontSize: 9,
            fontWeight: FontWeight.bold,
            letterSpacing: 0.5,
            color: Colors.white60,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          value,
          style: TextStyle(
            fontSize: 12,
            fontWeight: FontWeight.bold,
            color: color,
          ),
        ),
      ],
    );
  }
}
