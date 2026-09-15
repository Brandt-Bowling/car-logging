import 'dart:io' show File;

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
import 'google_drive_sync_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<Car> _cars = [];
  int _currentTab = 0;
  bool _isSpeedDialOpen = false;
  final Set<String> _dismissedWarningCarIds = {};

  bool get _hasUrgentService {
    for (final car in _cars) {
      if (car.odometer == null) continue;
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
    _loadCars();
  }



  void _loadCars() {
    setState(() {
      _cars = StorageService.getCars();
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
                    leading: const Icon(Icons.directions_car),
                    title: Text('${car.year} ${car.make} ${car.model}'),
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
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
      bottomNavigationBar: NavigationBar(
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

  Widget _buildGarageTab(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Garage'),
        actions: [
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
      ),
      body: _cars.isEmpty
          ? const Center(child: Text('No vehicles added yet.'))
          : ListView(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
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
      if (car.odometer == null || _dismissedWarningCarIds.contains(car.id)) continue;
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

    // Tire tread wear progress calculations
    final installedOdo = car.odometer != null ? (car.odometer! * 0.85).toInt() : 0;
    const estimatedTreadLife = 60000;
    final treadWearPercent = car.odometer != null
        ? (1.0 - (car.odometer! - installedOdo) / estimatedTreadLife).clamp(0.0, 1.0)
        : 1.0;

    // Next Service progress calculations (7.5k tire rotation interval for EV, 5k oil change for gas)
    final latestRecordOdo = records.isNotEmpty
        ? records.map((r) => r.odometer).reduce((a, b) => a > b ? a : b)
        : (car.odometer ?? 0);
    final serviceInterval = car.isEv ? 7500 : 5000;
    final nextServiceOdo = latestRecordOdo + serviceInterval;
    final remainingMiles = car.odometer != null ? nextServiceOdo - car.odometer! : serviceInterval;
    final servicePercent = (remainingMiles / serviceInterval.toDouble()).clamp(0.0, 1.0);

    return Card(
      margin: const EdgeInsets.only(bottom: 16.0),
      elevation: 2.0,
      color: theme.colorScheme.surfaceContainer,
      shadowColor: theme.brightness == Brightness.dark
          ? Colors.black.withValues(alpha: 0.6)
          : theme.colorScheme.shadow.withValues(alpha: 0.12),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16.0),
        side: BorderSide(
          color: theme.colorScheme.outlineVariant.withValues(alpha: 0.7),
          width: 1.2,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () {
          Navigator.push(
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
        },
        onLongPress: () => _deleteCarFlow(car),
        borderRadius: BorderRadius.circular(16.0),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Vehicle info header: Clean typography without EV/Gas tags
              Row(
                children: [
                  Container(
                    width: 60,
                    height: 60,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(10.0),
                      border: Border.all(
                        color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
                        width: 1,
                      ),
                      image: _getCarCardImage(car),
                    ),
                    child: car.imageUrl == null && car.localImagePath == null
                        ? Icon(Icons.directions_car, size: 30, color: theme.colorScheme.primary)
                        : null,
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${car.year} ${car.make} ${car.model}',
                          style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                        ),
                        if (car.licensePlate != null && car.licensePlate!.isNotEmpty)
                          Text(
                            'License: ${car.licensePlate}',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                      ],
                    ),
                  ),
                  Icon(Icons.chevron_right, color: theme.colorScheme.outline),
                ],
              ),
              const SizedBox(height: 14),

              // Side-by-Side Metric Cards: Mileage & Cost
              Row(
                children: [
                  // Left Card: Odometer / Mileage
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(12.0),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
                        borderRadius: BorderRadius.circular(12.0),
                        border: Border.all(
                          color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Row(
                                children: [
                                  Icon(Icons.speed, size: 15, color: theme.colorScheme.primary),
                                  const SizedBox(width: 5),
                                  Text(
                                    'Odometer',
                                    style: theme.textTheme.labelMedium?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                              InkWell(
                                onTap: () => _updateOdometerFlowForCar(car),
                                borderRadius: BorderRadius.circular(12),
                                child: Padding(
                                  padding: const EdgeInsets.all(2.0),
                                  child: Icon(
                                    Icons.edit_outlined,
                                    size: 14,
                                    color: theme.colorScheme.primary,
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Text(
                            car.odometer != null
                                ? '${NumberFormat.decimalPattern().format(car.odometer)} mi'
                                : 'Not set',
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),

                  // Right Card: Total Spent
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(12.0),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
                        borderRadius: BorderRadius.circular(12.0),
                        border: Border.all(
                          color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5),
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Row(
                                children: [
                                  Icon(Icons.payments_outlined, size: 15, color: Colors.green),
                                  const SizedBox(width: 5),
                                  Text(
                                    'Total Spent',
                                    style: theme.textTheme.labelMedium?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                              Text(
                                '${records.length} ${records.length == 1 ? 'log' : 'logs'}',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.outline,
                                  fontSize: 10,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          Text(
                            NumberFormat.simpleCurrency().format(carTotalSpent),
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: Colors.green,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // Progress bar: Tire Tread Wear
              Text(
                'Tire Tread Life Wear',
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline),
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  Expanded(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: treadWearPercent,
                        minHeight: 8,
                        backgroundColor: theme.colorScheme.surfaceContainerHighest,
                        valueColor: AlwaysStoppedAnimation<Color>(
                          treadWearPercent > 0.4 ? Colors.green : Colors.orange,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Text(
                    '${(treadWearPercent * 100).toInt()}% Remaining',
                    style: theme.textTheme.bodySmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: treadWearPercent > 0.4 ? Colors.green : Colors.orange,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // Progress bar: Next Service
              Text(
                car.isEv ? 'Next Tire Rotation' : 'Next Oil Change',
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline),
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  Expanded(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: servicePercent,
                        minHeight: 8,
                        backgroundColor: theme.colorScheme.surfaceContainerHighest,
                        valueColor: AlwaysStoppedAnimation<Color>(
                          remainingMiles > (car.isEv ? 1500 : 1000) ? Colors.green : Colors.orange,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Text(
                    remainingMiles > 0
                        ? '${remainingMiles.toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]},')} mi remaining'
                        : 'Due Now',
                    style: theme.textTheme.bodySmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: remainingMiles > (car.isEv ? 1500 : 1000) ? Colors.green : Colors.orange,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Document Status Badges
              Row(
                children: [
                  Expanded(
                    child: _buildDocBadge(context, Icons.description_outlined, 'Registration', true),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _buildDocBadge(context, Icons.security_outlined, 'Insurance', false),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _buildDocBadge(context, Icons.menu_book_outlined, 'Manual', true),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
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

    return Scaffold(
      appBar: AppBar(
        title: const Text('Activity'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            tooltip: 'Add Record',
            onPressed: _addMaintenanceRecordFlow,
          ),
        ],
      ),
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
              padding: const EdgeInsets.all(16.0),
              itemCount: allRecords.length,
              separatorBuilder: (context, index) => const SizedBox(height: 10),
              itemBuilder: (context, index) {
                final entry = allRecords[index];
                final car = entry.key;
                final record = entry.value;

                return Card(
                  elevation: 1,
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

    return Scaffold(
      appBar: AppBar(
        title: const Text('Schedule'),
      ),
      body: _cars.isEmpty
          ? const Center(child: Text('No vehicles added yet.'))
          : ListView.separated(
              padding: const EdgeInsets.all(16.0),
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

    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
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

  Widget _buildDocBadge(BuildContext context, IconData icon, String label, bool isUploaded) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      decoration: BoxDecoration(
        color: isUploaded
            ? colorScheme.primaryContainer.withValues(alpha: 0.7)
            : colorScheme.surfaceContainerHighest.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: isUploaded
              ? colorScheme.primary.withValues(alpha: 0.6)
              : colorScheme.outline.withValues(alpha: 0.3),
          width: 1,
        ),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon,
            size: 16,
            color: isUploaded ? colorScheme.primary : colorScheme.onSurfaceVariant,
          ),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: isUploaded ? colorScheme.onPrimaryContainer : colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }

  DecorationImage? _getCarCardImage(Car car) {
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
    if (provider == null) return null;
    return DecorationImage(image: provider, fit: BoxFit.cover);
  }
}
