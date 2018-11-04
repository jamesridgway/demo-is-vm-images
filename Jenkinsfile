node {
	stage("Clone repository") {
        checkout scm
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'github-token', sh 'git submodule update --init'
    }
	stage("Ubuntu 18.04") {
		sh './ubuntu-1804/build.sh'
	}
}
def parallelBuilds = [:]
def directories = ["jenkins-master", "jenkins-slave", "rails-base"]
directories.each { directory -> 
	parallelBuilds[directory] = {
			node {
				stage("Clone repository") {
		        checkout scm
		        sh 'git submodule update --init'
		    }
			stage(directory) {
				sh "./" + directory + "/build.sh"
			}
		}
	}
}
parallel parallelBuilds