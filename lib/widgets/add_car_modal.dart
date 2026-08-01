import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:uuid/uuid.dart';

import '../models/car.dart';

class AddCarModal extends StatefulWidget {
  const AddCarModal({super.key});

  @override
  State<AddCarModal> createState() => _AddCarModalState();
}

class _AddCarModalState extends State<AddCarModal> {
  final _formKey = GlobalKey<FormState>();

  String _make = '';
  String _model = '';
  int? _year;
  String? _imageUrl;
  String? _licensePlate;
  String? _vin;
  int? _odometer;
  bool _isEv = false;

  void _submitForm() {
    if (_formKey.currentState!.validate()) {
      _formKey.currentState!.save();

      final newCar = Car(
        id: const Uuid().v4(),
        make: _make,
        model: _model,
        year: _year ?? DateTime.now().year,
        imageUrl: _imageUrl?.isEmpty ?? true ? null : _imageUrl,
        licensePlate: _licensePlate?.isEmpty ?? true ? null : _licensePlate,
        vin: _vin?.isEmpty ?? true ? null : _vin,
        odometer: _odometer,
        isEv: _isEv,
      );

      Navigator.pop(context, newCar);
    }
  }

  @override
  Widget build(BuildContext context) {
    // DraggableScrollableSheet is ideal for a modal that can be expanded
    return DraggableScrollableSheet(
      initialChildSize: 0.6, // Start slightly taller to fit basic fields nicely
      minChildSize: 0.4,
      maxChildSize: 0.9,
      expand: false,
      builder: (context, scrollController) {
        return Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(28.0)),
          ),
          child: CustomScrollView(
            controller: scrollController,
            slivers: [
              SliverToBoxAdapter(
                child: Column(
                  children: [
                    const SizedBox(height: 12),
                    // Drag handle
                    Container(
                      width: 48,
                      height: 4,
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.onSurfaceVariant.withValues(alpha: 0.4),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text(
                      'Add New Vehicle',
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Swipe up for more details',
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 24.0),
                sliver: SliverToBoxAdapter(
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        _buildAnimatedField(
                          delay: 0.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'Make *',
                              hintText: 'e.g., Toyota',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.directions_car_outlined),
                            ),
                            textInputAction: TextInputAction.next,
                            textCapitalization: TextCapitalization.words,
                            validator: (value) {
                              if (value == null || value.trim().isEmpty) {
                                return 'Make is required';
                              }
                              return null;
                            },
                            onSaved: (value) => _make = value!.trim(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildAnimatedField(
                          delay: 100.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'Model *',
                              hintText: 'e.g., Camry',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.car_repair_outlined),
                            ),
                            textInputAction: TextInputAction.next,
                            textCapitalization: TextCapitalization.words,
                            validator: (value) {
                              if (value == null || value.trim().isEmpty) {
                                return 'Model is required';
                              }
                              return null;
                            },
                            onSaved: (value) => _model = value!.trim(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildAnimatedField(
                          delay: 200.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'Year',
                              hintText: 'e.g., 2024',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.calendar_today_outlined),
                            ),
                            keyboardType: TextInputType.number,
                            textInputAction: TextInputAction.next,
                            onSaved: (value) {
                              if (value != null && value.isNotEmpty) {
                                _year = int.tryParse(value);
                              }
                            },
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildAnimatedField(
                          delay: 300.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'License Plate',
                              hintText: 'e.g., ABC-1234',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.badge_outlined),
                            ),
                            textInputAction: TextInputAction.next,
                            textCapitalization: TextCapitalization.characters,
                            onSaved: (value) => _licensePlate = value?.trim(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildAnimatedField(
                          delay: 400.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'VIN',
                              hintText: 'Vehicle Identification Number',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.pin_outlined),
                            ),
                            textInputAction: TextInputAction.next,
                            textCapitalization: TextCapitalization.characters,
                            onSaved: (value) => _vin = value?.trim(),
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildAnimatedField(
                          delay: 500.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'Current Odometer (miles)',
                              hintText: 'e.g., 45000',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.speed),
                            ),
                            keyboardType: TextInputType.number,
                            textInputAction: TextInputAction.next,
                            validator: (value) {
                              if (value != null && value.isNotEmpty) {
                                final val = int.tryParse(value);
                                if (val == null || val < 0) {
                                  return 'Please enter a valid odometer reading';
                                }
                              }
                              return null;
                            },
                            onSaved: (value) {
                              if (value != null && value.isNotEmpty) {
                                _odometer = int.tryParse(value);
                              }
                            },
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildAnimatedField(
                          delay: 550.ms,
                          child: SwitchListTile(
                            title: const Text('Electric Vehicle (EV)'),
                            subtitle: const Text('Disables oil change reminders'),
                            value: _isEv,
                            secondary: const Icon(Icons.electric_car),
                            onChanged: (bool value) {
                              setState(() {
                                _isEv = value;
                              });
                            },
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildAnimatedField(
                          delay: 650.ms,
                          child: TextFormField(
                            decoration: const InputDecoration(
                              labelText: 'Image URL',
                              hintText: 'https://...',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.image_outlined),
                            ),
                            keyboardType: TextInputType.url,
                            textInputAction: TextInputAction.done,
                            onFieldSubmitted: (_) => _submitForm(),
                            onSaved: (value) => _imageUrl = value?.trim(),
                          ),
                        ),
                        const SizedBox(height: 32),
                        _buildAnimatedField(
                          delay: 750.ms,
                          child: FilledButton.icon(
                            onPressed: _submitForm,
                            icon: const Icon(Icons.add),
                            label: const Text('Add Vehicle'),
                            style: FilledButton.styleFrom(
                              padding: const EdgeInsets.symmetric(vertical: 16),
                            ),
                          ),
                        ),
                        const SizedBox(height: 32),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildAnimatedField({required Widget child, required Duration delay}) {
    return child
        .animate()
        .fadeIn(duration: 400.ms, delay: delay)
        .slideY(begin: 0.2, end: 0, duration: 400.ms, delay: delay, curve: Curves.easeOutQuad);
  }
}
