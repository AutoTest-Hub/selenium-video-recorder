#!/bin/bash

# All Test Suites Runner for Selenium Video Recording Project
# This script provides quick access to all available test suites

echo "🧪 Selenium Video Recording - All Test Suites Runner"
echo "===================================================="

# Color codes
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}ℹ️${NC} $1"
}

print_header() {
    echo -e "${CYAN}🎯${NC} $1"
}

print_option() {
    echo -e "${GREEN}$1${NC} $2"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

echo ""
print_info "Available Test Suites:"
echo ""

print_header "Original Test Suites:"
print_option "1)" "Basic Video Recording Tests (run-tests-mac.sh)"
print_option "2)" "SmartWait Framework Tests (run-wait-tests-mac.sh)"

echo ""
print_header "New Flickering Detection Test Suites:"
print_option "3)" "🔍 Flickering Detection Tests - Focused (~10 min)"
print_option "4)" "🎯 Comprehensive Test Suite - All tests (~45 min)" 
print_option "5)" "⚡ Stress Tests - System limits (~20 min)"
print_option "6)" "🔄 Parallel Tests - Concurrency (~15 min)"
print_option "7)" "🎨 Visual Validation Tests - Frame quality (~15 min)"
print_option "8)" "💨 Smoke Tests - Quick validation (~5 min)"
print_option "9)" "🏃‍♂️ Marathon Tests - Extreme duration (~10+ min)"

echo ""
print_header "Quick Commands:"
echo ""

echo "# Original test suites:"
echo "./run-tests-mac.sh                    # Basic video recording tests"
echo "./run-wait-tests-mac.sh               # SmartWait framework tests"
echo ""

echo "# New flickering detection test suites:"
echo "./run-flickering-tests-mac.sh                     # Flickering detection (default)"
echo "./run-flickering-tests-mac.sh comprehensive       # All new tests"
echo "./run-flickering-tests-mac.sh stress              # Stress tests only"
echo "./run-flickering-tests-mac.sh parallel            # Parallel tests only"
echo "./run-flickering-tests-mac.sh visual              # Visual validation only"
echo "./run-flickering-tests-mac.sh smoke               # Quick smoke tests"
echo "./run-flickering-tests-mac.sh marathon            # Extreme duration tests"
echo ""

echo "# With verbose output:"
echo "./run-flickering-tests-mac.sh comprehensive -v    # Comprehensive with details"
echo "./run-flickering-tests-mac.sh --help              # Show all options"
echo ""

echo "# Individual test classes (Maven):"
echo "mvn test -Dtest=\"FlickeringReproductionTest\"        # Flickering reproduction only"
echo "mvn test -Dtest=\"VideoRecordingStressTest\"         # Stress tests only"
echo "mvn test -Dtest=\"ParallelVideoRecordingTest\"       # Parallel tests only"
echo "mvn test -Dtest=\"VisualValidationTest\"             # Visual validation only"
echo ""

print_warning "Recommended for Flickering Issues:"
echo "1. Start with: ./run-flickering-tests-mac.sh"
echo "2. If issues found: ./run-flickering-tests-mac.sh comprehensive"
echo "3. For deep analysis: ./run-flickering-tests-mac.sh stress"

echo ""
print_info "What's New in the Enhanced Test Suite:"
echo "• 40+ new test methods designed to reproduce flickering"
echo "• Stress testing under extreme conditions"
echo "• Parallel execution testing for race conditions"  
echo "• Visual validation for frame consistency"
echo "• Comprehensive logging and performance analysis"
echo "• Multiple specialized test configurations"

echo ""
print_info "Test Suite Comparison:"
echo ""
echo "Original Tests (2 classes, 7 methods):"
echo "  ✓ Basic single and multi-tab recording"
echo "  ✓ Video speed control"
echo ""
echo "New Enhanced Tests (4 classes, 40+ methods):"
echo "  ✓ Flickering reproduction scenarios"
echo "  ✓ High-load stress testing"
echo "  ✓ Parallel execution validation"
echo "  ✓ Visual consistency checking"
echo "  ✓ Performance benchmarking"
echo "  ✓ Resource contention detection"
echo "  ✓ Animation smoothness validation"
echo ""

read -p "Enter the number of the test suite you want to run (1-9), or press Enter to see help: " choice

case $choice in
    1)
        echo "Running basic video recording tests..."
        ./run-tests-mac.sh
        ;;
    2)
        echo "Running SmartWait framework tests..."
        ./run-wait-tests-mac.sh
        ;;
    3)
        echo "Running flickering detection tests..."
        ./run-flickering-tests-mac.sh flickering
        ;;
    4)
        echo "Running comprehensive test suite..."
        ./run-flickering-tests-mac.sh comprehensive
        ;;
    5)
        echo "Running stress tests..."
        ./run-flickering-tests-mac.sh stress
        ;;
    6)
        echo "Running parallel tests..."
        ./run-flickering-tests-mac.sh parallel
        ;;
    7)
        echo "Running visual validation tests..."
        ./run-flickering-tests-mac.sh visual
        ;;
    8)
        echo "Running smoke tests..."
        ./run-flickering-tests-mac.sh smoke
        ;;
    9)
        echo "Running marathon tests..."
        ./run-flickering-tests-mac.sh marathon
        ;;
    "")
        echo ""
        print_info "For detailed help on the new flickering tests:"
        echo "./run-flickering-tests-mac.sh --help"
        echo ""
        print_info "For basic usage:"
        echo "./run-flickering-tests-mac.sh              # Run flickering detection"
        echo "./run-flickering-tests-mac.sh comprehensive # Run all new tests"
        ;;
    *)
        echo "Invalid choice. Run './run-flickering-tests-mac.sh --help' for more options."
        ;;
esac