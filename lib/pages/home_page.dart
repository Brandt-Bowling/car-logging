import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';
import 'package:flutter_animate/flutter_animate.dart';

import '../models/car.dart';
import '../models/maintenance_record.dart';
import '../widgets/add_car_modal.dart';
import 'car_details_page.dart';
import '../services/storage_service.dart';
import 'google_drive_sync_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with SingleTickerProviderStateMixin {
  List<Car> _cars = [];
  int _currentTab = 0;
  bool _isSpeedDialOpen = false;
  late AnimationController _animationController;
  late Animation<double> _rotationAnimation;

  @override
  void initState() {
    super.initState();
    _loadCars();
    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 250),
    );
    _rotationAnimation = Tween<double>(begin: 0.0, end: 0.125).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeOut),
    ); // 0.125 turns = 45 degrees
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  void _loadCars() {
    setState(() {
      _cars = StorageService.getCars();
    });
  }

  void _toggleSpeedDial() {
    setState(() {
      _isSpeedDialOpen = !_isSpeedDialOpen;
      if (_isSpeedDialOpen) {
        _animationController.forward();
      } else {
        _animationController.reverse();
      }
    });
  }

  void _closeSpeedDial() {
    if (_isSpeedDialOpen) {
      setState(() {
        _isSpeedDialOpen = false;
        _animationController.reverse();
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

  Future<void> _addMaintenanceRecordFlow() async {
    _closeSpeedDial();
    final car = await _selectCar(context, 'Select Vehicle for Maintenance');
    if (car == null) return;

    if (!mounted) return;

    final titleController = TextEditingController();
    final odometerController = TextEditingController(
      text: car.odometer != null ? car.odometer.toString() : '',
    );
    final costController = TextEditingController();
    final descriptionController = TextEditingController();
    DateTime selectedDate = DateTime.now();

    final result = await showDialog<MaintenanceRecord>(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: Text('Add Maintenance for ${car.make}'),
              content: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: titleController,
                      decoration: const InputDecoration(
                        labelText: 'Service Title *',
                        hintText: 'e.g. Oil Change',
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: odometerController,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(
                        labelText: 'Odometer Reading (miles) *',
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: costController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(
                        labelText: 'Cost (\$)',
                        prefixText: '\$',
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: descriptionController,
                      decoration: const InputDecoration(
                        labelText: 'Notes',
                      ),
                    ),
                    const SizedBox(height: 16),
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('Date of Service'),
                      subtitle: Text(selectedDate.toLocal().toString().split(' ')[0]),
                      trailing: const Icon(Icons.calendar_today),
                      onTap: () async {
                        final date = await showDatePicker(
                          context: context,
                          initialDate: selectedDate,
                          firstDate: DateTime(2000),
                          lastDate: DateTime.now().add(const Duration(days: 365)),
                        );
                        if (date != null) {
                          setDialogState(() {
                            selectedDate = date;
                          });
                        }
                      },
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Cancel'),
                ),
                FilledButton(
                  onPressed: () {
                    if (titleController.text.trim().isEmpty) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Title is required')),
                      );
                      return;
                    }
                    final odo = int.tryParse(odometerController.text);
                    if (odo == null || odo < 0) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Please enter a valid odometer')),
                      );
                      return;
                    }
                    final cost = double.tryParse(costController.text);
                    final newRecord = MaintenanceRecord(
                      id: const Uuid().v4(),
                      carId: car.id,
                      title: titleController.text.trim(),
                      date: selectedDate,
                      odometer: odo,
                      cost: cost,
                      description: descriptionController.text.trim(),
                    );
                    Navigator.pop(context, newRecord);
                  },
                  child: const Text('Save'),
                ),
              ],
            );
          },
        );
      },
    );

    if (result != null) {
      await StorageService.addMaintenanceRecord(result);

      if (car.odometer == null || result.odometer > car.odometer!) {
        final updatedCar = car.copyWith(odometer: result.odometer);
        final cars = StorageService.getCars();
        final idx = cars.indexWhere((c) => c.id == car.id);
        if (idx != -1) {
          cars[idx] = updatedCar;
          await StorageService.saveCars(cars);
        }
      }

      _loadCars();

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Added maintenance record: ${result.title}')),
      );
    }
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
              const GoogleDriveSyncPage(isTab: true),
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
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.garage),
            label: 'Garage',
          ),
          NavigationDestination(
            icon: Icon(Icons.cloud_sync),
            label: 'Drive Sync',
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _toggleSpeedDial,
        child: AnimatedRotation(
          turns: _rotationAnimation.value,
          duration: const Duration(milliseconds: 250),
          child: Icon(_isSpeedDialOpen ? Icons.close : Icons.add),
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
    final theme = Theme.of(context);

    // Calculate Dashboard Statistics
    final totalMileage = _cars.fold<int>(0, (sum, car) => sum + (car.odometer ?? 0));
    double totalSpent = 0.0;
    for (var car in _cars) {
      final records = StorageService.getMaintenanceRecords(car.id);
      for (var r in records) {
        totalSpent += (r.cost ?? 0.0);
      }
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('My Garage'),
        actions: [
          IconButton(
            icon: const Icon(Icons.cloud_sync),
            tooltip: 'Sync Google Drive',
            onPressed: () async {
              setState(() {
                _currentTab = 1; // switch to sync tab
              });
            },
          ),
        ],
      ),
      body: _cars.isEmpty
          ? const Center(child: Text('No vehicles added yet.'))
          : ListView(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
              children: [
                // Dashboard Stats Header Row
                Row(
                  children: [
                    Expanded(
                      child: Card(
                        color: theme.colorScheme.secondaryContainer,
                        elevation: 1,
                        child: Padding(
                          padding: const EdgeInsets.all(16.0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Icon(Icons.speed, color: theme.colorScheme.primary, size: 20),
                                  const SizedBox(width: 8),
                                  Text(
                                    'Total Mileage',
                                    style: theme.textTheme.titleSmall?.copyWith(
                                      color: theme.colorScheme.onSecondaryContainer.withValues(alpha: 0.8),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                '${NumberFormat.decimalPattern().format(totalMileage)} mi',
                                style: theme.textTheme.headlineSmall?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: theme.colorScheme.onSecondaryContainer,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Card(
                        color: theme.colorScheme.tertiaryContainer,
                        elevation: 1,
                        child: Padding(
                          padding: const EdgeInsets.all(16.0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Icon(Icons.monetization_on, color: theme.colorScheme.tertiary, size: 20),
                                  const SizedBox(width: 8),
                                  Text(
                                    'Total Spent',
                                    style: theme.textTheme.titleSmall?.copyWith(
                                      color: theme.colorScheme.onTertiaryContainer.withValues(alpha: 0.8),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                NumberFormat.simpleCurrency().format(totalSpent),
                                style: theme.textTheme.headlineSmall?.copyWith(
                                  fontWeight: FontWeight.bold,
                                  color: theme.colorScheme.onTertiaryContainer,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Text(
                  'Vehicles',
                  style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),

                // Vehicle list cards
                ..._cars.map((car) => _buildVehicleCard(context, car)),
              ],
            ),
    );
  }

  Widget _buildVehicleCard(BuildContext context, Car car) {
    final theme = Theme.of(context);
    final records = StorageService.getMaintenanceRecords(car.id);

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
        borderRadius: BorderRadius.circular(12.0),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Vehicle info header
              Row(
                children: [
                  Container(
                    width: 60,
                    height: 60,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(8.0),
                      image: car.imageUrl != null
                          ? DecorationImage(
                              image: NetworkImage(car.imageUrl!),
                              fit: BoxFit.cover,
                            )
                          : null,
                    ),
                    child: car.imageUrl == null
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
                        if (car.licensePlate != null)
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
              const Divider(height: 24),

              // Odometer row
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    car.odometer != null
                        ? '${NumberFormat.decimalPattern().format(car.odometer)} miles'
                        : 'Odometer not set',
                    style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  TextButton.icon(
                    icon: const Icon(Icons.edit, size: 16),
                    label: const Text('Update'),
                    onPressed: () => _updateOdometerFlowForCar(car),
                  ),
                ],
              ),
              const SizedBox(height: 12),

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
                  _buildDocBadge(context, Icons.description, 'Registration', true),
                  const SizedBox(width: 8),
                  _buildDocBadge(context, Icons.security, 'Insurance', false),
                  const SizedBox(width: 8),
                  _buildDocBadge(context, Icons.book, 'Manual', true),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDocBadge(BuildContext context, IconData icon, String label, bool isUploaded) {
    final colorScheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isUploaded ? colorScheme.primaryContainer : colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: isUploaded ? colorScheme.primary : colorScheme.outline.withValues(alpha: 0.5),
          width: 1,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            size: 14,
            color: isUploaded ? colorScheme.onPrimaryContainer : colorScheme.onSurfaceVariant,
          ),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w500,
              color: isUploaded ? colorScheme.onPrimaryContainer : colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
