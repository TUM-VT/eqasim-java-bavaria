#!/bin/bash
#
# Run EQASim only with Slurm job.
#
#SBATCH -J muc_2040_10pct
#SBATCH --output=simulation_munich_2040_10pct_output_%j.log
#SBATCH --error=simulation_munich_2040_10pct_error_%j.log
#SBATCH --nodes=1
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=48
#SBATCH --mem=80GB
#SBATCH --time=48:00:00
#SBATCH --account=pn39mu
#SBATCH --partition=micro
#SBATCH --mail-type=BEGIN,END,FAIL
#SBATCH --mail-user=elena.natterer@tum.de
#SBATCH --get-user-env
#SBATCH --export=none

set -euo pipefail

# SuperMUC-NG: initialize module environment.
module load slurm_setup
# module load openjdk/21.0.3_9
# module load miniconda/3

#export HOME=/dss/dsshome1/06/ge49wav3
export FONTCONFIG_PATH=$HOME/test_java_font_4u/fonts
export FONTCONFIG_FILE=$HOME/test_java_font_4u/fonts/fonts.conf
export FC_FONT_PATH=$HOME/test_java_font_4u/fonts

EQASIM_DIR="${SLURM_SUBMIT_DIR}"
JAVA_LOG="${EQASIM_DIR}/output_munich_2040_10pct.log"

CONFIG_PATH="${EQASIM_DIR}/populations/muenchen_2040_10pct/munich_config_sbatch.xml"
OUTPUT_DIR="${EQASIM_DIR}/simulation_output/munich_2040_10pct"

srun --cpus-per-task=48 /bin/bash -lc "
  cd \"${EQASIM_DIR}\" &&
  echo \"[$(date -Is)] Starting MATSim\" > \"${JAVA_LOG}\" &&
  java \
    -Xms50g -Xmx80g \
    -Djava.awt.headless=true \
    -cp bavaria/target/bavaria-2.0.0.jar \
    org.eqasim.bavaria.RunSimulation \
    --config-path \"${CONFIG_PATH}\" \
    --config:global.numberOfThreads 12 \
    --config:controller.outputDirectory \"${OUTPUT_DIR}\" \
    --config:controller.overwriteFiles failIfDirectoryExists \
    >> \"${JAVA_LOG}\" 2>&1
"
