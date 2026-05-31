#!/bin/bash
#SBATCH --job-name=carla-singularity
#SBATCH --output=/projects/jumob/carla-server-%j.out
#SBATCH --error=/projects/jumob/carla-server-%j.err
#SBATCH --partition=GPUNodes
#SBATCH --gres=gpu:1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=8
#SBATCH --time=04:00:00

echo "NODE: $(hostname)"
nvidia-smi

# On execute CARLA headless en utilisant le conteneur officiel (il contient l'environnement Ubuntu 18.04 parfait)
singularity exec --nv /projects/jumob/carla_0.9.15.sif \
    /bin/bash /home/carla/CarlaUE4.sh -RenderOffScreen -nosound -carla-server -carla-port=2000 -opengl
